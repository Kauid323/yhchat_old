package com.nago8.chat.old.cache;

import android.content.Context;

import com.nago8.chat.old.proto.chat_ws_go.WsMsg;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.WsMsgConverter;
import com.nago8.chat.old.ws.WsClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 全局会话数据与未读消息计数缓存单例。
 * 负责统一管理会话列表、置顶状态、免打扰状态以及未读消息总数的计算与通知。
 */
public class ConversationCache {

    private static final ConversationCache instance = new ConversationCache();

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

    // 内存数据存储：LinkedHashMap 保持顺序并强行按 chatId 去重
    private final LinkedHashMap<String, ConversationList.ConversationData> conversationMap = new LinkedHashMap<>();
    private final Set<String> stickySet = new HashSet<>();
    private final Set<String> dndSet = new HashSet<>();

    private OnUnreadCountChangeListener unreadChangeListener;

    private int totalUnreadCount = 0;
    private int stickyUnreadCount = 0;

    private ConversationCache() {}

    public synchronized void setOnUnreadCountChangeListener(OnUnreadCountChangeListener listener) {
        this.unreadChangeListener = listener;
    }

    /**
     * 更新全量会话列表（来自服务器网络接口 /v1/conversation/list 或本地缓存）
     */
    public synchronized void updateConversationList(List<ConversationList.ConversationData> list) {
        if (list != null) {
            conversationMap.clear();
            for (ConversationList.ConversationData cd : list) {
                if (cd != null && cd.chat_id != null && !cd.chat_id.isEmpty()) {
                    conversationMap.put(cd.chat_id, cd);
                    if (cd.do_not_disturb != 0) {
                        dndSet.add(cd.chat_id);
                    }
                }
            }
            recalculateUnreadCounts();
        }
    }

    /**
     * 更新置顶会话列表
     */
    public synchronized void updateStickyList(List<StickyInfo> stickyList) {
        stickySet.clear();
        if (stickyList != null) {
            for (StickyInfo s : stickyList) {
                if (s != null && s.chatId != null && !s.chatId.isEmpty()) {
                    stickySet.add(s.chatId);
                }
            }
        }
        recalculateUnreadCounts();
    }

    /**
     * 更新免打扰会话集合
     */
    public synchronized void updateDoNotDisturbSet(Collection<String> dndIds) {
        dndSet.clear();
        if (dndIds != null) {
            dndSet.addAll(dndIds);
        }
        recalculateUnreadCounts();
    }

    /**
     * 获取全量会话列表
     */
    public synchronized List<ConversationList.ConversationData> getConversationList() {
        return new ArrayList<>(conversationMap.values());
    }

    /**
     * 获取置顶会话列表
     */
    public synchronized List<ConversationList.ConversationData> getStickyConversationDataList() {
        List<ConversationList.ConversationData> result = new ArrayList<>();
        for (String chatId : stickySet) {
            ConversationList.ConversationData conv = conversationMap.get(chatId);
            if (conv != null) {
                result.add(conv);
            }
        }
        return result;
    }

    /**
     * 将指定会话标记为已读（未读数重置为 0）
     */
    public synchronized void markAsRead(String chatId) {
        if (chatId == null || chatId.isEmpty()) return;
        ConversationList.ConversationData old = conversationMap.get(chatId);
        if (old != null) {
            ConversationList.ConversationData updated = old.newBuilder()
                    .unread_message(0)
                    .build();
            conversationMap.put(chatId, updated);
            recalculateUnreadCounts();
        }
    }

    /**
     * 收到 WebSocket 实时推送消息处理
     */
    public synchronized void onPushMessage(WsMsg wsMsg, Context ctx) {
        if (wsMsg == null || wsMsg.chat_id == null || wsMsg.chat_id.isEmpty()) return;

        // 屏蔽消息直接忽略
        if (WsClient.isBlockedMessage(wsMsg)) return;

        String chatId = wsMsg.chat_id;
        String myUserId = PrefUtils.getUserId(ctx);
        boolean isFromMe = (wsMsg.sender != null && wsMsg.sender.chat_id != null && wsMsg.sender.chat_id.equals(myUserId));
        String activeChatId = WsClient.getInstance().getActiveChatId();
        boolean isActiveChat = (activeChatId != null && activeChatId.equals(chatId));

        String senderName = (wsMsg.sender != null && wsMsg.sender.name != null) ? wsMsg.sender.name : "";
        String preview = WsMsgConverter.toPreviewText(wsMsg, ctx);
        String chatContent = !senderName.isEmpty() ? senderName + ":" + preview : preview;

        ConversationList.ConversationData oldData = conversationMap.get(chatId);
        int newUnread;
        if (isFromMe || isActiveChat) {
            newUnread = 0;
        } else {
            int currentUnread = (oldData != null) ? oldData.unread_message : 0;
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
            String name = senderName;
            String avatarUrl = (wsMsg.sender != null && wsMsg.sender.avatar_url != null) ? wsMsg.sender.avatar_url : "";
            newData = new ConversationList.ConversationData.Builder()
                    .chat_id(chatId)
                    .chat_type(wsMsg.chat_type != 0 ? wsMsg.chat_type : 1)
                    .name(name)
                    .avatar_url(avatarUrl)
                    .unread_message(newUnread)
                    .chat_content(chatContent)
                    .timestamp_ms(wsMsg.timestamp)
                    .build();
        }

        // 移到顶部：先移除再插入
        conversationMap.remove(chatId);
        LinkedHashMap<String, ConversationList.ConversationData> newMap = new LinkedHashMap<>();
        newMap.put(chatId, newData);
        newMap.putAll(conversationMap);

        conversationMap.clear();
        conversationMap.putAll(newMap);

        recalculateUnreadCounts();
    }

    /**
     * 重新计算未读消息总数（剔除免打扰与屏蔽项）
     */
    public synchronized void recalculateUnreadCounts() {
        int total = 0;
        int sticky = 0;

        for (ConversationList.ConversationData cd : conversationMap.values()) {
            if (cd == null || cd.chat_id == null || cd.chat_id.isEmpty()) continue;

            // 免打扰会话不计入未读总数（检查数据结构中的 do_not_disturb 以及全局 dndSet）
            if (cd.do_not_disturb != 0 || dndSet.contains(cd.chat_id)) continue;

            int unread = cd.unread_message;
            if (unread > 0) {
                total += unread;
                if (stickySet.contains(cd.chat_id)) {
                    sticky += unread;
                }
            }
        }

        this.totalUnreadCount = total;
        this.stickyUnreadCount = sticky;

        if (unreadChangeListener != null) {
            unreadChangeListener.onUnreadCountChanged(totalUnreadCount, stickyUnreadCount);
        }
    }

    public synchronized int getTotalUnreadCount() {
        return totalUnreadCount;
    }

    public synchronized int getStickyUnreadCount() {
        return stickyUnreadCount;
    }

    public synchronized void clearCache() {
        conversationMap.clear();
        stickySet.clear();
        dndSet.clear();
        totalUnreadCount = 0;
        stickyUnreadCount = 0;
        if (unreadChangeListener != null) {
            unreadChangeListener.onUnreadCountChanged(0, 0);
        }
    }
}
