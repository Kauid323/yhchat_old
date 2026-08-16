package com.nago8.chat.old.cache;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nago8.chat.old.proto.conversation.ConversationList;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 归档会话管理器单例。
 * 负责归档会话的本地持久化、内存拦截判断、以及归档/取消归档操作。
 */
public class ArchiveManager {

    private static final String PREF_NAME = "archived_conversations_pref";
    private static final String KEY_ARCHIVED_DATA = "archived_data_json";

    private static final ArchiveManager instance = new ArchiveManager();

    public static ArchiveManager getInstance() {
        return instance;
    }

    public static class ArchivedConversation {
        public String chatId;
        public int chatType;
        public String name;
        public String avatarUrl;
        public String lastContent;
        public long timestamp;
        public long archivedAt;

        public ArchivedConversation() {}

        public ArchivedConversation(String chatId, int chatType, String name, String avatarUrl, String lastContent, long timestamp) {
            this.chatId = chatId;
            this.chatType = chatType;
            this.name = name != null ? name : "";
            this.avatarUrl = avatarUrl != null ? avatarUrl : "";
            this.lastContent = lastContent != null ? lastContent : "";
            this.timestamp = timestamp;
            this.archivedAt = System.currentTimeMillis();
        }
    }

    private final Set<String> archivedIdSet = new HashSet<>();
    private final LinkedHashMap<String, ArchivedConversation> archivedMap = new LinkedHashMap<>();
    private boolean isLoaded = false;
    private final Gson gson = new Gson();

    private ArchiveManager() {}

    /**
     * 确保已从本地 SharedPreferences 加载归档数据
     */
    public synchronized void ensureLoaded(Context context) {
        if (isLoaded || context == null) return;
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_ARCHIVED_DATA, null);
        if (json != null && !json.isEmpty()) {
            try {
                Type type = new TypeToken<List<ArchivedConversation>>() {}.getType();
                List<ArchivedConversation> list = gson.fromJson(json, type);
                if (list != null) {
                    archivedIdSet.clear();
                    archivedMap.clear();
                    for (ArchivedConversation item : list) {
                        if (item != null && item.chatId != null && !item.chatId.isEmpty()) {
                            archivedIdSet.add(item.chatId);
                            archivedMap.put(item.chatId, item);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        isLoaded = true;
    }

    /**
     * 判断某个会话是否已处于归档状态
     */
    public synchronized boolean isArchived(String chatId) {
        return chatId != null && archivedIdSet.contains(chatId);
    }

    /**
     * 判断某个会话是否已处于归档状态（附带上下文懒加载）
     */
    public synchronized boolean isArchived(Context context, String chatId) {
        ensureLoaded(context);
        return isArchived(chatId);
    }

    /**
     * 归档某个会话
     */
    public synchronized void archiveConversation(Context context, ConversationList.ConversationData data) {
        if (data == null || data.chat_id == null || data.chat_id.isEmpty()) return;
        ensureLoaded(context);

        String chatId = data.chat_id;
        int chatType = data.chat_type != 0 ? data.chat_type : 1;
        String name = data.name != null ? data.name : "";
        String avatarUrl = data.avatar_url != null ? data.avatar_url : "";
        String content = data.chat_content != null ? data.chat_content : "";
        long time = data.timestamp_ms;

        ArchivedConversation ac = new ArchivedConversation(chatId, chatType, name, avatarUrl, content, time);
        archivedIdSet.add(chatId);
        archivedMap.put(chatId, ac);

        saveToPrefs(context);

        // 同步从主会话缓存与置顶列表中移除
        ConversationCache.getInstance().removeConversationFromMainList(chatId);
        ConversationCache.getInstance().removeStickyConversation(chatId);
    }

    /**
     * 取消归档某个会话（恢复至主列表）
     */
    public synchronized ArchivedConversation unarchiveConversation(Context context, String chatId) {
        if (chatId == null || chatId.isEmpty()) return null;
        ensureLoaded(context);

        archivedIdSet.remove(chatId);
        ArchivedConversation removed = archivedMap.remove(chatId);

        saveToPrefs(context);
        return removed;
    }

    /**
     * 获取所有已归档会话列表（按归档时间倒序）
     */
    public synchronized List<ArchivedConversation> getArchivedList(Context context) {
        ensureLoaded(context);
        List<ArchivedConversation> list = new ArrayList<>(archivedMap.values());
        Collections.reverse(list);
        return list;
    }

    /**
     * 获取已归档会话数量
     */
    public synchronized int getArchivedCount(Context context) {
        ensureLoaded(context);
        return archivedIdSet.size();
    }

    private void saveToPrefs(Context context) {
        if (context == null) return;
        try {
            SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            List<ArchivedConversation> list = new ArrayList<>(archivedMap.values());
            String json = gson.toJson(list);
            sp.edit().putString(KEY_ARCHIVED_DATA, json).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
