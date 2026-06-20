package com.nago8.chat.old.ws;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.nago8.chat.old.R;
import com.nago8.chat.old.utils.NotificationHelper;
import com.nago8.chat.old.utils.WsMsgConverter;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.chat_ws_go.INFO;
import com.nago8.chat.old.proto.chat_ws_go.bot_board_message;
import com.nago8.chat.old.proto.chat_ws_go.draft_input;
import com.nago8.chat.old.proto.chat_ws_go.edit_message;
import com.nago8.chat.old.proto.chat_ws_go.file_send_message;
import com.nago8.chat.old.proto.chat_ws_go.heartbeat_ack;
import com.nago8.chat.old.proto.chat_ws_go.push_message;
import com.nago8.chat.old.proto.chat_ws_go.stream_message;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WsClient {

    public interface MessageListener {
        void onPushMessage(WsMsg msg);
    }

    private static final String WS_URL = "wss://chat-ws-go.jwzhd.com/ws";
    private static final long HEARTBEAT_INTERVAL_MS = 30 * 1000L;
    private static final long RECONNECT_DELAY_MS = 5 * 1000L;
    private static final int MAX_RECONNECT_DELAY_MS = 60 * 1000;

    private static WsClient instance;

    // 通知相关
    private Context appContext;
    private String activeChatId = null;

    /**
     * 免打扰判断接口，由 HomeActivity 实现。
     */
    public interface DndChecker {
        boolean isDoNotDisturb(String chatId);
    }
    private DndChecker dndChecker;

    private OkHttpClient client;
    private WebSocket webSocket;
    private String userId;
    private String token;
    private String deviceId;
    private boolean connected = false;
    private Thread heartbeatThread;
    private volatile boolean running = false;

    // 多 listener 支持，后台/前台可同时监听
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 重连相关
    private volatile boolean shouldReconnect = false;
    private int reconnectAttempt = 0;

    /**
     * 添加消息监听器（支持多个同时监听）
     */
    public void addMessageListener(MessageListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除消息监听器
     */
    public void removeMessageListener(MessageListener listener) {
        listeners.remove(listener);
    }

    /**
     * 兼容旧接口：设置单个监听器（会先清空再设）
     */
    public void setMessageListener(MessageListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private WsClient() {
        client = ApiClient.getClient().newBuilder()
                .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public static synchronized WsClient getInstance() {
        if (instance == null) {
            instance = new WsClient();
        }
        return instance;
    }

    /**
     * 设置 ApplicationContext 用于发通知。
     */
    public void setAppContext(Context ctx) {
        this.appContext = ctx != null ? ctx.getApplicationContext() : null;
    }

    /**
     * 设置免打扰判断器（由 HomeActivity 实现）。
     */
    public void setDndChecker(DndChecker checker) {
        this.dndChecker = checker;
    }

    /**
     * 设置当前正在聊天的会话 ID，该会话不发通知。
     */
    public void setActiveChatId(String chatId) {
        this.activeChatId = chatId;
    }

    public void connect(String userId, String token) {
        if (connected) {
            WsLogManager.getInstance().logInfo("already connected, skip");
            return;
        }
        this.userId = userId;
        this.token = token;
        this.deviceId = "android_" + Build.MANUFACTURER + "_" + Build.MODEL;
        this.shouldReconnect = true;
        this.reconnectAttempt = 0;

        doConnect();
    }

    private void doConnect() {
        WsLogManager.getInstance().logInfo("connecting to " + WS_URL);

        Request request = new Request.Builder()
                .url(WS_URL)
                .build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                WsClient.this.webSocket = webSocket;
                connected = true;
                reconnectAttempt = 0;
                WsLogManager.getInstance().logInfo("WebSocket connected");
                sendLogin();
                startHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                byte[] raw = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                String parsed = tryDecodeMessage(raw);
                if (parsed != null) {
                    WsLogManager.getInstance().logRecv(parsed);
                } else {
                    WsLogManager.getInstance().logRecv("[text] " + text);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleBinaryMessage(bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                WsLogManager.getInstance().logInfo("closing: " + code + " " + reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                connected = false;
                stopHeartbeat();
                WsLogManager.getInstance().logInfo("closed: " + code + " " + reason);
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connected = false;
                stopHeartbeat();
                WsLogManager.getInstance().logError("failure: " + t.getMessage());
                scheduleReconnect();
            }
        });
    }

    /**
     * 断线后自动重连，延迟递增（5s → 10s → 20s → 40s → 60s 封顶）
     */
    private void scheduleReconnect() {
        if (!shouldReconnect) return;
        reconnectAttempt++;
        int delay = (int) Math.min(RECONNECT_DELAY_MS * (1L << (reconnectAttempt - 1)), MAX_RECONNECT_DELAY_MS);
        WsLogManager.getInstance().logInfo("reconnect in " + delay + "ms (attempt " + reconnectAttempt + ")");

        mainHandler.postDelayed(() -> {
            if (shouldReconnect && !connected) {
                doConnect();
            }
        }, delay);
    }

    public void disconnect() {
        shouldReconnect = false;
        stopHeartbeat();
        mainHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(1000, "user disconnect");
            webSocket = null;
        }
        connected = false;
        WsLogManager.getInstance().logInfo("disconnected");
    }

    public boolean isConnected() {
        return connected;
    }

    private void sendLogin() {
        String seq = String.valueOf(System.currentTimeMillis());
        String json = "{\"seq\":\"" + seq + "\",\"cmd\":\"login\",\"data\":{\"userId\":\"" + userId + "\",\"token\":\"" + token + "\",\"platform\":\"android\",\"deviceId\":\"" + deviceId + "\"}}";
        if (webSocket != null) {
            webSocket.send(json);
            WsLogManager.getInstance().logSend(json);
        }
    }

    private void startHeartbeat() {
        running = true;
        heartbeatThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
                if (!running || webSocket == null) break;
                String seq = String.valueOf(System.currentTimeMillis());
                String json = "{\"seq\":\"" + seq + "\",\"cmd\":\"heartbeat\",\"data\":{}}";
                webSocket.send(json);
                WsLogManager.getInstance().logSend(json);
            }
        }, "ws-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void stopHeartbeat() {
        running = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }

    private void handleBinaryMessage(ByteString bytes) {
        byte[] raw = bytes.toByteArray();
        String result = tryDecodeMessage(raw);
        if (result != null) {
            WsLogManager.getInstance().logRecv(result);
        } else {
            WsLogManager.getInstance().logError("decode failed size=" + raw.length);
        }
    }

    private void notifyListeners(WsMsg msg) {
        // 通知逻辑：非当前聊天、非免打扰的会话发系统通知
        handleNotification(msg);
        // 通知所有监听器
        for (MessageListener l : listeners) {
            l.onPushMessage(msg);
        }
    }

    /**
     * 通知逻辑：收到消息时判断是否需要发系统通知。
     * - 当前正在聊天的会话不发
     * - 免打扰会话不发
     */
    private void handleNotification(WsMsg msg) {
        if (appContext == null || msg == null || msg.chat_id == null) return;
        // 正在聊天的会话不通知
        if (msg.chat_id.equals(activeChatId)) return;
        // 免打扰会话不通知
        if (dndChecker != null && dndChecker.isDoNotDisturb(msg.chat_id)) return;
        // 构建通知内容
        String senderName = (msg.sender != null && msg.sender.name != null) ? msg.sender.name : "";
        String preview = WsMsgConverter.toPreviewText(msg, appContext);
        String content = senderName.length() > 0 ? senderName + ": " + preview : preview;
        int chatType = (msg.sender != null) ? msg.sender.chat_type : 1;
        String title = senderName.length() > 0 ? senderName : appContext.getString(R.string.notification_new_message);
        NotificationHelper.showMessageNotification(appContext, msg.chat_id, chatType, title, content);
    }

    private String tryDecodeMessage(byte[] raw) {
        String result;

        result = tryDecode(raw, push_message.ADAPTER, "push_message");
        if (result != null) return result;
        result = tryDecode(raw, heartbeat_ack.ADAPTER, "heartbeat_ack");
        if (result != null) return result;
        result = tryDecode(raw, edit_message.ADAPTER, "edit_message");
        if (result != null) return result;
        result = tryDecode(raw, draft_input.ADAPTER, "draft_input");
        if (result != null) return result;
        result = tryDecode(raw, stream_message.ADAPTER, "stream_message");
        if (result != null) return result;
        result = tryDecode(raw, file_send_message.ADAPTER, "file_send_message");
        if (result != null) return result;
        result = tryDecode(raw, bot_board_message.ADAPTER, "bot_board_message");
        if (result != null) return result;

        return null;
    }

    private String tryDecode(byte[] raw, com.squareup.wire.ProtoAdapter adapter, String typeName) {
        try {
            Object msg = adapter.decode(raw);
            if (msg instanceof push_message) {
                push_message m = (push_message) msg;
                String cmd = m.info != null && m.info.cmd != null ? m.info.cmd : "";
                String seq = m.info != null && m.info.seq != null ? m.info.seq : "";
                if (cmd.length() == 0) return null;
                String detail = "";
                if (m.data != null && m.data.msg != null) {
                    WsMsg wm = m.data.msg;
                    String senderName = wm.sender != null && wm.sender.name != null ? wm.sender.name : "";
                    String text = wm.content != null && wm.content.text != null ? wm.content.text : "";
                    detail = "from=" + senderName + " chat_id=" + wm.chat_id + " type=" + wm.content_type + " text=" + text;
                }
                // 通知所有监听器
                if (m.data != null && m.data.msg != null) {
                    notifyListeners(m.data.msg);
                }
                return "[cmd=" + cmd + " seq=" + seq + "] push_message " + detail;
            } else if (msg instanceof heartbeat_ack) {
                heartbeat_ack m = (heartbeat_ack) msg;
                String cmd = m.info != null && m.info.cmd != null ? m.info.cmd : "";
                String seq = m.info != null && m.info.seq != null ? m.info.seq : "";
                if (cmd.length() == 0) return null;
                return "[cmd=" + cmd + " seq=" + seq + "] heartbeat_ack";
            } else if (msg instanceof edit_message) {
                edit_message m = (edit_message) msg;
                String cmd = m.info != null && m.info.cmd != null ? m.info.cmd : "";
                String seq = m.info != null && m.info.seq != null ? m.info.seq : "";
                if (cmd.length() == 0) return null;
                return "[cmd=" + cmd + " seq=" + seq + "] edit_message";
            } else if (msg instanceof draft_input) {
                draft_input m = (draft_input) msg;
                String cmd = m.info != null && m.info.cmd != null ? m.info.cmd : "";
                String seq = m.info != null && m.info.seq != null ? m.info.seq : "";
                if (cmd.length() == 0) return null;
                String detail = "";
                if (m.data != null && m.data.draft != null) {
                    detail = "chat_id=" + m.data.draft.chat_id + " input=" + m.data.draft.input;
                }
                return "[cmd=" + cmd + " seq=" + seq + "] draft_input " + detail;
            } else if (msg instanceof stream_message) {
                stream_message m = (stream_message) msg;
                String cmd = m.info != null && m.info.cmd != null ? m.info.cmd : "";
                String seq = m.info != null && m.info.seq != null ? m.info.seq : "";
                if (cmd.length() == 0) return null;
                String detail = "";
                if (m.data != null && m.data.msg != null) {
                    detail = "msg_id=" + m.data.msg.msg_id + " content=" + m.data.msg.content;
                }
                return "[cmd=" + cmd + " seq=" + seq + "] stream_message " + detail;
            } else if (msg instanceof file_send_message) {
                file_send_message m = (file_send_message) msg;
                String cmd = m.info != null && m.info.cmd != null ? m.info.cmd : "";
                String seq = m.info != null && m.info.seq != null ? m.info.seq : "";
                if (cmd.length() == 0) return null;
                return "[cmd=" + cmd + " seq=" + seq + "] file_send_message";
            } else if (msg instanceof bot_board_message) {
                bot_board_message m = (bot_board_message) msg;
                String cmd = m.info != null && m.info.cmd != null ? m.info.cmd : "";
                String seq = m.info != null && m.info.seq != null ? m.info.seq : "";
                if (cmd.length() == 0) return null;
                return "[cmd=" + cmd + " seq=" + seq + "] bot_board_message";
            }
        } catch (Exception e) {
            // 解码失败，尝试下一个类型
        }
        return null;
    }
}
