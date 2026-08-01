package com.nago8.chat.old.model;

import org.json.JSONObject;

public class BaUserModel {
    private int id;
    private String userId;
    private String nickname;
    private String avatarUrl;

    public static BaUserModel fromJson(JSONObject json) {
        if (json == null) return null;
        BaUserModel model = new BaUserModel();
        model.id = json.optInt("id", 0);

        if (json.has("user_id")) {
            model.userId = json.optString("user_id", "");
        } else if (json.has("userId")) {
            model.userId = json.optString("userId", "");
        } else {
            model.userId = String.valueOf(model.id);
        }

        model.nickname = json.optString("nickname", "");
        if ("未知用户".equals(model.nickname) || "Unknown user".equals(model.nickname)) {
            model.nickname = "";
        }

        if (json.has("avatar_url")) {
            model.avatarUrl = json.optString("avatar_url", "");
        } else if (json.has("avatarUrl")) {
            model.avatarUrl = json.optString("avatarUrl", "");
        } else if (json.has("avatar")) {
            model.avatarUrl = json.optString("avatar", "");
        } else {
            model.avatarUrl = "";
        }

        return model;
    }

    public int getId() { return id; }
    public String getUserId() { return userId != null ? userId : ""; }
    public String getNickname() { return nickname != null ? nickname : ""; }
    public String getAvatarUrl() { return avatarUrl != null ? avatarUrl : ""; }
}
