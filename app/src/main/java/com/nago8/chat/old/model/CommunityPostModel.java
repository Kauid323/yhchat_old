package com.nago8.chat.old.model;

import org.json.JSONObject;

public class CommunityPostModel {
    private long id;
    private int baId;
    private String senderId;
    private String title;
    private String content;
    private int contentType;
    private String senderNickname;
    private String senderAvatar;
    private String createTimeText;
    private int likeNum;
    private int commentNum;
    private int collectNum;
    private double amountNum;
    private boolean isLiked;
    private boolean isCollected;

    private String displayAuthorName;
    private String likeNumStr;
    private String commentNumStr;
    private String collectNumStr;

    public static CommunityPostModel fromJson(JSONObject json) {
        if (json == null) return null;
        CommunityPostModel model = new CommunityPostModel();
        model.id = json.optLong("id", 0);
        model.baId = json.optInt("baId", 0);
        model.senderId = json.optString("senderId", "");
        model.title = json.optString("title", "");
        model.content = json.optString("content", "");
        model.contentType = json.optInt("contentType", 1);
        model.senderNickname = json.optString("senderNickname", "");
        model.senderAvatar = json.optString("senderAvatar", "");
        model.createTimeText = json.optString("createTimeText", "");
        if (model.createTimeText.isEmpty()) {
            long createTime = json.optLong("createTime", 0);
            if (createTime > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                model.createTimeText = sdf.format(new java.util.Date(createTime * 1000L));
            }
        }
        model.likeNum = json.optInt("likeNum", 0);
        model.commentNum = json.optInt("commentNum", 0);
        model.collectNum = json.optInt("collectNum", 0);
        model.amountNum = json.optDouble("amountNum", 0.0);

        model.displayAuthorName = (model.senderNickname != null && !model.senderNickname.isEmpty())
                ? model.senderNickname : model.senderId;
        model.likeNumStr = String.valueOf(model.likeNum);
        model.commentNumStr = String.valueOf(model.commentNum);
        model.collectNumStr = String.valueOf(model.collectNum);

        Object likedObj = json.opt("isLiked");
        if (likedObj != null) {
            model.isLiked = "1".equals(likedObj.toString()) || Boolean.TRUE.toString().equalsIgnoreCase(likedObj.toString());
        }

        Object collectedObj = json.opt("isCollected");
        if (collectedObj != null) {
            model.isCollected = "1".equals(collectedObj.toString()) || Boolean.TRUE.toString().equalsIgnoreCase(collectedObj.toString());
        }

        return model;
    }

    public long getId() { return id; }
    public int getBaId() { return baId; }
    public String getSenderId() { return senderId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public int getContentType() { return contentType; }
    public String getSenderNickname() { return senderNickname; }
    public String getSenderAvatar() { return senderAvatar; }
    public String getCreateTimeText() { return createTimeText; }
    public int getLikeNum() { return likeNum; }
    public int getCommentNum() { return commentNum; }
    public int getCollectNum() { return collectNum; }
    public double getAmountNum() { return amountNum; }
    public boolean isLiked() { return isLiked; }
    public boolean isCollected() { return isCollected; }

    public String getDisplayAuthorName() { return displayAuthorName != null ? displayAuthorName : ""; }
    public String getLikeNumStr() { return likeNumStr != null ? likeNumStr : "0"; }
    public String getCommentNumStr() { return commentNumStr != null ? commentNumStr : "0"; }
    public String getCollectNumStr() { return collectNumStr != null ? collectNumStr : "0"; }
}
