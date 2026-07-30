package com.nago8.chat.old.repository;

import com.nago8.chat.old.net.ApiClient;

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class CommunityRepository {

    private static final String BASE = ApiClient.BASE_URL;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // ==================== 通用回调 ====================

    public interface SimpleCallback {
        void onSuccess();
        void onError(String msg);
    }

    public interface StringCallback {
        void onSuccess(String responseBody);
        void onError(String msg);
    }

    // ==================== 内部工具 ====================

    private Call post(String path, String token, String jsonBody, StringCallback cb) {
        RequestBody body = RequestBody.create(JSON, jsonBody);
        Request req = new Request.Builder()
                .url(BASE + path)
                .header("token", token)
                .post(body)
                .build();
        Call call = ApiClient.getClient().newCall(req);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cb.onError(e.getMessage());
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    cb.onError("HTTP " + response.code());
                    return;
                }
                cb.onSuccess(response.body().string());
            }
        });
        return call;
    }

    private void simplePost(String path, String token, String jsonBody, SimpleCallback cb) {
        post(path, token, jsonBody, new StringCallback() {
            @Override
            public void onSuccess(String body) {
                try {
                    org.json.JSONObject root = new org.json.JSONObject(body);
                    if (root.optInt("code", 0) == 1) {
                        cb.onSuccess();
                    } else {
                        cb.onError(root.optString("msg", "failed"));
                    }
                } catch (Exception e) {
                    cb.onError(e.getMessage());
                }
            }
            @Override
            public void onError(String msg) {
                cb.onError(msg);
            }
        });
    }

    // ==================== 文章互动 ====================

    /** 点赞/取消点赞文章（接口幂等，重复调用自动切换） */
    public Call likePost(String token, long postId, SimpleCallback cb) {
        return post("/v1/community/posts/post-like", token,
                "{\"id\":" + postId + "}", new StringCallback() {
                    @Override public void onSuccess(String body) {
                        try {
                            org.json.JSONObject root = new org.json.JSONObject(body);
                            if (root.optInt("code", 0) == 1) cb.onSuccess();
                            else cb.onError(root.optString("msg", "failed"));
                        } catch (Exception e) { cb.onError(e.getMessage()); }
                    }
                    @Override public void onError(String msg) { cb.onError(msg); }
                });
    }

    /** 收藏/取消收藏文章 */
    public Call collectPost(String token, long postId, SimpleCallback cb) {
        return post("/v1/community/posts/post-collect", token,
                "{\"id\":" + postId + "}", new StringCallback() {
                    @Override public void onSuccess(String body) {
                        try {
                            org.json.JSONObject root = new org.json.JSONObject(body);
                            if (root.optInt("code", 0) == 1) cb.onSuccess();
                            else cb.onError(root.optString("msg", "failed"));
                        } catch (Exception e) { cb.onError(e.getMessage()); }
                    }
                    @Override public void onError(String msg) { cb.onError(msg); }
                });
    }

    /** 投币文章 */
    public Call rewardPost(String token, long postId, String recvId, double amount, SimpleCallback cb) {
        String json = "{\"postId\":" + postId + ",\"recvId\":\"" + recvId + "\",\"amount\":" + amount + "}";
        simplePost("/v1/community/posts/post-reward", token, json, cb);
        return null;
    }

    // ==================== 评论 ====================

    /** 获取评论列表 */
    public Call getCommentList(String token, long postId, int page, int size, StringCallback cb) {
        String json = "{\"postId\":" + postId + ",\"size\":" + size + ",\"page\":" + page + "}";
        return post("/v1/community/comment/comment-list", token, json, cb);
    }

    /** 发表评论，parentCommentId=0 表示直接评论文章，否则为楼中楼回复 */
    public Call sendComment(String token, long postId, long parentCommentId, String content, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("postId", postId);
            json.put("commentId", parentCommentId);
            json.put("content", content);
            simplePost("/v1/community/comment/comment", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
        return null;
    }

    /** 点赞/取消点赞评论 */
    public Call likeComment(String token, long commentId, SimpleCallback cb) {
        simplePost("/v1/community/comment/comment-like", token,
                "{\"id\":" + commentId + "}", cb);
        return null;
    }
}
