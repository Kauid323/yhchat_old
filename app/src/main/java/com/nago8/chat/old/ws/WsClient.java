package com.nago8.chat.old.ws;

import android.os.Build;

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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WsClient {

    private static final String WS_URL = "wss://chat-ws-go.jwzhd.com/ws";
    private static final long HEARTBEAT_INTERVAL_MS = 30 * 1000L;

    private static WsClient instance;

    private OkHttpClient client;
    private WebSocket webSocket;
    private String userId;
    private String token;
    private String deviceId;
    private boolean connected = false;
    private Thread heartbeatThread;
    private volatile boolean running = false;

    private WsClient() {
        // 复用 ApiClient 的 OkHttpClient（已配置 TLS 1.2 适配安卓4）
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

    public void connect(String userId, String token) {
        if (connected) {
            WsLogManager.getInstance().logInfo("already connected, skip");
            return;
        }
        this.userId = userId;
        this.token = token;
        this.deviceId = "android_" + Build.MANUFACTURER + "_" + Build.MODEL;

        WsLogManager.getInstance().logInfo("connecting to " + WS_URL);

        Request request = new Request.Builder()
                .url(WS_URL)
                .build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                WsClient.this.webSocket = webSocket;
                connected = true;
                WsLogManager.getInstance().logInfo("WebSocket connected");
                sendLogin();
                startHeartbeat();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // 服务器可能把二进制 protobuf 当文本帧发送，尝试用 ISO-8859-1 恢复原始字节再解码
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
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connected = false;
                stopHeartbeat();
                WsLogManager.getInstance().logError("failure: " + t.getMessage());
            }
        });
    }

    public void disconnect() {
        stopHeartbeat();
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

    private String tryDecodeMessage(byte[] raw) {
        // 服务器返回的是完整消息（push_message / heartbeat_ack 等），不是裸 INFO
        // 逐个尝试解码，取能成功且 info.cmd 有值的
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
