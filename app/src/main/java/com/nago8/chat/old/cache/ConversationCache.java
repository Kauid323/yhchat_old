package com.nago8.chat.old.cache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.WsMsgConverter;
import com.nago8.chat.old.ws.WsClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 全局会话数据与未读消息计数缓存单例。
 * 彻底重构未读计算逻辑：解决 WebSocket 多重监听重复累加、消息去重、已读状态与服务端实时同步。
 */
public class ConversationCache {

    private static final ConversationCache instance = new ConversationCache();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static ConversationCache getInstance() {
        return instance;
    }

    public static class StickyInfo {
        public String chatId;
        public int chatType;
        public String chatName;
        public String avatarUrl;
        public long sort;
    }

    public interface OnUnreadCountChangeListener {
        void onUnreadCountChanged(int totalUnread, int stickyUnread);
    }

    public interface OnConversationDataChangeListener {
        void onConversationDataChanged();
    }

    // 内存数据存储：LinkedHashMap 保持会话顺序并强行按 chatId 去重
    private final LinkedHashMap<String, ConversationList.ConversationData> conversationMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, ConversationList.ConversationData> stickyMap = new LinkedHashMap<>();
    private final Set<String> stickySet = new HashSet<>();
    private final Set<String> dndSet = new HashSet<>();

    // 消息列表缓存：按 chatId 映射 List<Msg>
    private final Map<String, List<Msg>> messageCacheMap = new HashMap<>();

    // 已处理 WS 消息 ID 去重缓存（容量 1000 的 LRU），彻底杜绝同一消息多次 WS 推送累加未读数
    private final Set<String> processedMsgIds = Collections.synchronizedSet(new LinkedHashSet<>());

    private final List<OnUnreadCountChangeListener> unreadListeners = new CopyOnWriteArrayList<>();
    private final List<OnConversationDataChangeListener> dataChangeListeners = new CopyOnWriteArrayList<>();

    private int totalUnreadCount = 0;
    private int stickyUnreadCount = 0;

    private ConversationCache() {}

    public void addOnUnreadCountChangeListener(OnUnreadCountChangeListener listener) {
        if (listener != null && !unreadListeners.contains(listener)) {
            unreadListeners.add(listener);
            listener.onUnreadCountChanged(totalUnreadCount, stickyUnreadCount);
        }
    }

    public void removeOnUnreadCountChangeListener(OnUnreadCountChangeListener listener) {
        if (listener != null) {
            unreadListeners.remove(listener);
        }
    }

    public void setOnUnreadCountChangeListener(OnUnreadCountChangeListener listener) {
        unreadListeners.clear();
        if (listener != null) {
            unreadListeners.add(listener);
            listener.onUnreadCountChanged(totalUnreadCount, stickyUnreadCount);
        }
    }

    public void addOnConversationDataChangeListener(OnConversationDataChangeListener listener) {
        if (listener != null && !dataChangeListeners.contains(listener)) {
            dataChangeListeners.add(listener);
        }
    }

    public void removeOnConversationDataChangeListener(OnConversationDataChangeListener listener) {
        if (listener != null) {
            dataChangeListeners.remove(listener);
        }
    }

    public synchronized List<Msg> getCachedMessages(String chatId) {
        if (chatId == null || chatId.isEmpty()) return new ArrayList<>();
        List<Msg> cached = messageCacheMap.get(chatId);
        return cached != null ? new ArrayList<>(cached) : new ArrayList<>();
    }

    public synchronized void updateCachedMessages(String chatId, List<Msg> messages) {
        if (chatId == null || chatId.isEmpty()) return;
        if (messages == null) {
            messageCacheMap.remove(chatId);
        } else {
            messageCacheMap.put(chatId, new ArrayList<>(messages));
        }
    }

    public synchronized void saveSinglePushMessage(WsMsg wsMsg, String myUserId) {
        if (wsMsg == null) return;
        String chatId = WsClient.getTargetChatId(wsMsg, myUserId);
        if (chatId == null || chatId.isEmpty()) return;

        Msg msg = WsMsgConverter.convert(wsMsg, myUserId);
        if (msg == null) return;

        boolean isRecallMsg = (msg.msg_delete_time > 0);

        List<Msg> list = messageCacheMap.get(chatId);
        if (list == null) {
            if (isRecallMsg) return;
            list = new ArrayList<>();
            messageCacheMap.put(chatId, list);
        }

        if (msg.msg_id != null && !msg.msg_id.isEmpty()) {
            int foundIndex = -1;
            for (int i = 0; i < list.size(); i++) {
                Msg existing = list.get(i);
                if (existing != null && msg.msg_id.equals(existing.msg_id)) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex != -1) {
                Msg merged = WsMsgConverter.mergeMsg(list.get(foundIndex), msg);
                list.set(foundIndex, merged);
                return;
            }
        }

        if (isRecallMsg) {
            return;
        }

        list.add(msg);
    }

    /**
     * 更新全量会话列表（来自网络接口 /v1/conversation/list）
     */
    public synchronized void updateConversationList(List<ConversationList.ConversationData> list) {
        if (list != null) {
            String activeChatId = WsClient.getInstance().getActiveChatId();
            conversationMap.clear();
            for (ConversationList.ConversationData cd : list) {
                if (cd != null && cd.chat_id != null && !cd.chat_id.isEmpty()) {
                    if (ArchiveManager.getInstance().isArchived(cd.chat_id)) {
                        continue;
                    }
                    ConversationList.ConversationData finalData = cd;
                    // 如果当前该会话正在聊天界面打开，强制置未读数为 0
                    if (activeChatId != null && activeChatId.equals(cd.chat_id) && cd.unread_message > 0) {
                        finalData = cd.newBuilder().unread_message(0).build();
                    }
                    conversationMap.put(cd.chat_id, finalData);
                    if (cd.do_not_disturb != 0) {
                        dndSet.add(cd.chat_id);
                    }
                }
            }
            recalculateUnreadCounts();
            notifyDataChanged();
        }
    }

    /**
     * 更新置顶会话列表
     */
    public synchronized void updateStickyList(List<StickyInfo> stickyList) {
        stickySet.clear();
        stickyMap.clear();
        if (stickyList != null) {
            for (StickyInfo s : stickyList) {
                if (s != null && s.chatId != null && !s.chatId.isEmpty()) {
                    if (ArchiveManager.getInstance().isArchived(s.chatId)) {
                        continue;
                    }
                    stickySet.add(s.chatId);

                    ConversationList.ConversationData mainConv = conversationMap.get(s.chatId);
                    if (mainConv != null) {
                        stickyMap.put(s.chatId, mainConv);
                    } else {
                        ConversationList.ConversationData convData = new ConversationList.ConversationData.Builder()
                                .chat_id(s.chatId)
                                .chat_type(s.chatType != 0 ? s.chatType : 1)
                                .name(s.chatName != null ? s.chatName : "")
                                .avatar_url(s.avatarUrl != null ? s.avatarUrl : "")
                                .chat_content("")
                                .unread_message(0)
                                .build();
                        stickyMap.put(s.chatId, convData);
                    }
                }
            }
        }
        recalculateUnreadCounts();
        notifyDataChanged();
    }

    public synchronized boolean isSticky(String chatId) {
        return chatId != null && stickySet.contains(chatId);
    }

    public synchronized void removeConversationFromMainList(String chatId) {
        if (chatId == null || chatId.isEmpty()) return;
        conversationMap.remove(chatId);
        recalculateUnreadCounts();
        notifyDataChanged();
    }

    public synchronized void removeStickyConversation(String chatId) {
        if (chatId == null || chatId.isEmpty()) return;
        stickySet.remove(chatId);
        stickyMap.remove(chatId);
        recalculateUnreadCounts();
        notifyDataChanged();
    }

    public synchronized void updateDoNotDisturbSet(Collection<String> dndIds) {
        dndSet.clear();
        if (dndIds != null) {
            dndSet.addAll(dndIds);
        }
        recalculateUnreadCounts();
    }

    public synchronized List<ConversationList.ConversationData> getConversationList() {
        return new ArrayList<>(conversationMap.values());
    }

    public synchronized List<ConversationList.ConversationData> getStickyConversationDataList() {
        List<ConversationList.ConversationData> result = new ArrayList<>();
        for (String chatId : stickySet) {
            ConversationList.ConversationData mainConv = conversationMap.get(chatId);
            if (mainConv != null) {
                result.add(mainConv);
            } else {
                ConversationList.ConversationData stickyConv = stickyMap.get(chatId);
                if (stickyConv != null) {
                    result.add(stickyConv);
                }
            }
        }
        return result;
    }

    /**
     * 将指定会话标记为已读（本地清零未读数并向服务端发送 dismiss-notification 同步）
     */
    public void markAsRead(Context ctx, String chatId) {
        if (chatId == null || chatId.isEmpty()) return;

        synchronized (this) {
            ConversationList.ConversationData old = conversationMap.get(chatId);
            if (old != null && old.unread_message > 0) {
                ConversationList.ConversationData updated = old.newBuilder()
                        .unread_message(0)
                        .build();
                conversationMap.put(chatId, updated);
            }
            ConversationList.ConversationData stickyOld = stickyMap.get(chatId);
            if (stickyOld != null && stickyOld.unread_message > 0) {
                ConversationList.ConversationData stickyUpdated = stickyOld.newBuilder()
                        .unread_message(0)
                        .build();
                stickyMap.put(chatId, stickyUpdated);
            }
            recalculateUnreadCounts();
            notifyDataChanged();
        }

        // 向服务器异步发送已读同步通知
        if (ctx != null) {
            String token = PrefUtils.getToken(ctx);
            if (!TextUtils.isEmpty(token)) {
                String json = "{\"chatId\":\"" + chatId + "\"}";
                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        json
                );
                Request request = new Request.Builder()
                        .url(ApiClient.BASE_URL + "/v1/conversation/dismiss-notification")
                        .header("token", token)
                        .post(body)
                        .build();
                ApiClient.getClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        response.close();
                    }
                });
            }
        }
    }

    public synchronized void markAsRead(String chatId) {
        markAsRead(null, chatId);
    }

    /**
     * 收到 WebSocket 实时推送消息处理（核心未读计数与防膨胀处理）
     */
    public synchronized void onPushMessage(WsMsg wsMsg, Context ctx) {
        if (wsMsg == null) return;
        if (WsClient.isBlockedMessage(wsMsg)) return;

        String myUserId = PrefUtils.getUserId(ctx);
        String chatId = WsClient.getTargetChatId(wsMsg, myUserId);
        if (chatId == null || chatId.isEmpty()) return;

        // 提前缓存单条消息实体
        saveSinglePushMessage(wsMsg, myUserId);

        // 如果已归档，不增加未读数，不刷进会话主列表
        if (ArchiveManager.getInstance().isArchived(ctx, chatId)) {
            return;
        }

        boolean isFromMe = (wsMsg.sender != null && wsMsg.sender.chat_id != null && wsMsg.sender.chat_id.equals(myUserId));
        String activeChatId = WsClient.getInstance().getActiveChatId();
        boolean isActiveChat = (activeChatId != null && activeChatId.equals(chatId));

        String senderName = (wsMsg.sender != null && wsMsg.sender.name != null) ? wsMsg.sender.name : "";
        String preview = WsMsgConverter.toPreviewText(wsMsg, ctx);
        String chatContent;
        if (preview != null && preview.startsWith("该消息已于")) {
            chatContent = preview;
        } else {
            chatContent = !senderName.isEmpty() ? senderName + ":" + preview : preview;
        }

        // 消息去重防护：若该消息 ID 已经处理过，仅更新消息内容和时间戳，绝不重复 +1 未读数！
        boolean alreadyProcessed = false;
        if (wsMsg.msg_id != null && !wsMsg.msg_id.isEmpty()) {
            if (processedMsgIds.contains(wsMsg.msg_id)) {
                alreadyProcessed = true;
            } else {
                if (processedMsgIds.size() > 1000) {
                    processedMsgIds.clear();
                }
                processedMsgIds.add(wsMsg.msg_id);
            }
        }

        // 判断是否为撤回消息或编辑消息
        boolean isRecallOrEdit = (wsMsg.delete_time > 0) || (wsMsg.edit_time > 0);

        ConversationList.ConversationData oldData = conversationMap.get(chatId);
        int currentUnread = (oldData != null) ? Math.max(0, oldData.unread_message) : 0;
        int newUnread;

        if (isFromMe || isActiveChat) {
            newUnread = 0;
        } else if (alreadyProcessed || isRecallOrEdit) {
            newUnread = currentUnread;
        } else {
            newUnread = currentUnread + 1;
        }

        ConversationList.ConversationData newData;
        if (oldData != null) {
            newData = oldData.newBuilder()
                    .unread_message(newUnread)
                    .chat_content(chatContent)
                    .timestamp_ms(wsMsg.timestamp)
                    .build();
        } else {
            String avatarUrl = (wsMsg.sender != null && wsMsg.sender.avatar_url != null) ? wsMsg.sender.avatar_url : "";
            newData = new ConversationList.ConversationData.Builder()
                    .chat_id(chatId)
                    .chat_type(wsMsg.chat_type != 0 ? wsMsg.chat_type : 1)
                    .name(senderName)
                    .avatar_url(avatarUrl)
                    .unread_message(newUnread)
                    .chat_content(chatContent)
                    .timestamp_ms(wsMsg.timestamp)
                    .build();
        }

        conversationMap.remove(chatId);
        LinkedHashMap<String, ConversationList.ConversationData> newMap = new LinkedHashMap<>();
        newMap.put(chatId, newData);
        newMap.putAll(conversationMap);

        conversationMap.clear();
        conversationMap.putAll(newMap);

        recalculateUnreadCounts();
        notifyDataChanged();
    }

    /**
     * 重新计算未读消息总数（剔除免打扰与归档项）
     */
    public synchronized void recalculateUnreadCounts() {
        int total = 0;
        int sticky = 0;

        for (ConversationList.ConversationData cd : conversationMap.values()) {
            if (cd == null || cd.chat_id == null || cd.chat_id.isEmpty()) continue;

            if (ArchiveManager.getInstance().isArchived(cd.chat_id)) continue;
            if (cd.do_not_disturb != 0 || dndSet.contains(cd.chat_id)) continue;

            int unread = Math.max(0, cd.unread_message);
            if (unread > 0) {
                total += unread;
                if (stickySet.contains(cd.chat_id)) {
                    sticky += unread;
                }
            }
        }

        this.totalUnreadCount = total;
        this.stickyUnreadCount = sticky;

        notifyUnreadChanged();
    }

    private void notifyUnreadChanged() {
        final int total = totalUnreadCount;
        final int sticky = stickyUnreadCount;
        mainHandler.post(() -> {
            for (OnUnreadCountChangeListener listener : unreadListeners) {
                listener.onUnreadCountChanged(total, sticky);
            }
        });
    }

    private void notifyDataChanged() {
        mainHandler.post(() -> {
            for (OnConversationDataChangeListener listener : dataChangeListeners) {
                listener.onConversationDataChanged();
            }
        });
    }

    public synchronized int getTotalUnreadCount() {
        return totalUnreadCount;
    }

    public synchronized int getStickyUnreadCount() {
        return stickyUnreadCount;
    }

    public synchronized void clearCache() {
        conversationMap.clear();
        stickyMap.clear();
        stickySet.clear();
        dndSet.clear();
        messageCacheMap.clear();
        processedMsgIds.clear();
        totalUnreadCount = 0;
        stickyUnreadCount = 0;
        notifyUnreadChanged();
        notifyDataChanged();
    }
}
