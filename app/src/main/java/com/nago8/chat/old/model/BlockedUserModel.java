package com.nago8.chat.old.model;

import org.json.JSONObject;

public class BlockedUserModel {
    private long id;
    private String userId;
    private String nickname;
    private String avatarUrl;

    public static BlockedUserModel fromJson(JSONObject json) {
        if (json == null) return null;
        BlockedUserModel model = new BlockedUserModel();
        model.id = json.optLong("id", 0);
        model.userId = json.optString("user_id", "");
        if (model.userId.isEmpty()) {
            model.userId = json.optString("userId", "");
        }
        model.nickname = json.optString("nickname", "");
        model.avatarUrl = json.optString("avatar_url", "");
        if (model.avatarUrl.isEmpty()) {
            model.avatarUrl = json.optString("avatarUrl", "");
        }
        return model;
    }

    public long getId() { return id; }
    public String getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }
}
