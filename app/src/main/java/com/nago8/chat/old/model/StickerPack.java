package com.nago8.chat.old.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StickerPack implements Serializable {
    public long id;
    public String name;
    public String createBy;
    public long createTime;
    public int delFlag;
    public int userCount;
    public int hot;
    public String uuid;
    public long updateTime;
    public int sort;
    public List<StickerItem> stickerItems = new ArrayList<>();

    // 详情中的创建者信息
    public String creatorUserId;
    public String creatorNickname;
    public String creatorAvatarUrl;

    public StickerPack() {
    }

    public StickerPack(long id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * 获取表情包封面/第一个表情的完整 URL
     */
    public String getCoverUrl() {
        if (stickerItems != null && !stickerItems.isEmpty()) {
            return stickerItems.get(0).getFullUrl();
        }
        return "";
    }
}
