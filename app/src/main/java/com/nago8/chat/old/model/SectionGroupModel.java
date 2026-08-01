package com.nago8.chat.old.model;

import org.json.JSONObject;

public class SectionGroupModel {
    private String groupId;
    private String name;
    private String introduction;
    private String avatarUrl;
    private int headcount;
    private String category;

    public static SectionGroupModel fromJson(JSONObject json) {
        if (json == null) return null;
        SectionGroupModel model = new SectionGroupModel();
        model.groupId = json.optString("groupId", "");
        model.name = json.optString("name", "");
        model.introduction = json.optString("introduction", "");
        model.avatarUrl = json.optString("avatarUrl", "");
        model.headcount = json.optInt("headcount", 0);
        model.category = json.optString("category", "");
        return model;
    }

    public String getGroupId() { return groupId != null ? groupId : ""; }
    public String getName() { return name != null ? name : ""; }
    public String getIntroduction() { return introduction != null ? introduction : ""; }
    public String getAvatarUrl() { return avatarUrl != null ? avatarUrl : ""; }
    public int getHeadcount() { return headcount; }
    public String getCategory() { return category != null ? category : ""; }
}
