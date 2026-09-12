package com.nago8.chat.old.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DiscoveryModels {

    public static class BannerItem implements Serializable {
        public int id;
        public String title;
        public String introduction;
        public String targetId;
        public String targetUrl;
        public String imageUrl;
        public int sort;
        public int typ;
    }

    public static class BotItem implements Serializable {
        public String chatId;
        public int chatType = 3;
        public String headcount;
        public String nickname;
        public String introduction;
        public String instructions;
        public String avatarUrl;

        public String getDisplayName() {
            return nickname != null && !nickname.isEmpty() ? nickname : (chatId != null ? chatId : "");
        }
    }

    public static class GroupItem implements Serializable {
        public long id;
        public String groupId;
        public String name;
        public String introduction;
        public String avatarUrl;
        public int headcount;
        public String category;
        public int categoryId;
        public int alwaysAgree; // 1: 直接进群, 0: 审核
        public int readHistory;
        public int isPrivate;

        public String getDisplayName() {
            return name != null && !name.isEmpty() ? name : (groupId != null ? groupId : "");
        }
    }

    public static class CategoryItem implements Serializable {
        public int id;
        public String name;
        public int parentId;
        public List<CategoryItem> subItems = new ArrayList<>();

        public CategoryItem() {}

        public CategoryItem(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
