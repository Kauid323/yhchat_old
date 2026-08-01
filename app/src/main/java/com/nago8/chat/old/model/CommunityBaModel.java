package com.nago8.chat.old.model;

import org.json.JSONObject;

public class CommunityBaModel {
    private int id;
    private String name;
    private String avatar;
    private int memberNum;
    private int postNum;
    private int groupNum;
    private String createTimeText;
    private String isFollowed;
    private long lastActive;
    private long createTime;

    public static CommunityBaModel fromJson(JSONObject json) {
        if (json == null) return null;
        CommunityBaModel model = new CommunityBaModel();
        model.id = json.optInt("id", 0);
        model.name = json.optString("name", "");
        model.avatar = json.optString("avatar", "");
        model.memberNum = json.optInt("memberNum", 0);
        model.postNum = json.optInt("postNum", 0);
        model.groupNum = json.optInt("groupNum", 0);
        model.createTimeText = json.optString("createTimeText", "");
        model.isFollowed = json.optString("isFollowed", "0");
        model.lastActive = json.optLong("lastActive", 0);
        model.createTime = json.optLong("createTime", 0);
        return model;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getAvatar() { return avatar; }
    public int getMemberNum() { return memberNum; }
    public int getPostNum() { return postNum; }
    public int getGroupNum() { return groupNum; }
    public String getCreateTimeText() { return createTimeText != null ? createTimeText : ""; }
    public String getIsFollowed() { return isFollowed != null ? isFollowed : "0"; }
    public void setIsFollowed(String isFollowed) { this.isFollowed = isFollowed; }
    public void setMemberNum(int memberNum) { this.memberNum = memberNum; }
    public long getLastActive() { return lastActive; }
    public long getCreateTime() { return createTime; }

    public String getStatsText(android.content.Context context) {
        if (context != null) {
            return context.getString(com.nago8.chat.old.R.string.community_ba_stats_format, memberNum, postNum);
        }
        return memberNum + " 成员  •  " + postNum + " 帖子";
    }


}
