package com.nago8.chat.old.cache;

import com.nago8.chat.old.model.Expression;
import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.model.StickerPack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话级表情包与收藏表情缓存管理器。
 * 首次进入面板加载成功后直接常驻内存，后续打开面板或切换 Tab 立即使用内存缓存秒开渲染，
 * 下次应用冷启动时重新向服务器同步一次最新数据。
 */
public class StickerMemoryCache {
    private static volatile boolean initialized = false;
    private static final List<Expression> favoriteExpressions = new ArrayList<>();
    private static final List<StickerPack> stickerPacks = new ArrayList<>();
    private static final Map<Long, List<StickerItem>> packItemsMap = new ConcurrentHashMap<>();

    public static boolean isInitialized() {
        return initialized;
    }

    public static synchronized List<Expression> getFavoriteExpressions() {
        return new ArrayList<>(favoriteExpressions);
    }

    public static synchronized void setFavoriteExpressions(List<Expression> list) {
        favoriteExpressions.clear();
        if (list != null) {
            favoriteExpressions.addAll(list);
        }
        initialized = true;
    }

    public static synchronized List<StickerPack> getStickerPacks() {
        return new ArrayList<>(stickerPacks);
    }

    public static synchronized void setStickerPacks(List<StickerPack> list) {
        stickerPacks.clear();
        if (list != null) {
            for (StickerPack pack : list) {
                if (pack != null) {
                    List<StickerItem> cachedItems = packItemsMap.get(pack.id);
                    if ((pack.stickerItems == null || pack.stickerItems.isEmpty()) && cachedItems != null && !cachedItems.isEmpty()) {
                        pack.stickerItems = new ArrayList<>(cachedItems);
                    }
                    stickerPacks.add(pack);
                }
            }
        }
        initialized = true;
    }

    public static List<StickerItem> getPackItems(long packId) {
        return packItemsMap.get(packId);
    }

    public static void putPackItems(long packId, List<StickerItem> items) {
        if (items != null) {
            packItemsMap.put(packId, new ArrayList<>(items));
            // 同步更新 stickerPacks 中的对应 pack
            synchronized (StickerMemoryCache.class) {
                for (StickerPack pack : stickerPacks) {
                    if (pack != null && pack.id == packId) {
                        pack.stickerItems = new ArrayList<>(items);
                        break;
                    }
                }
            }
        }
    }

    public static synchronized void clearSessionCache() {
        initialized = false;
        favoriteExpressions.clear();
        stickerPacks.clear();
        packItemsMap.clear();
    }
}
