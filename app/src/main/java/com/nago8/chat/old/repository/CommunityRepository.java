package com.nago8.chat.old.repository;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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

    @SuppressWarnings("UnusedReturnValue")
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                cb.onError(e.getMessage());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    cb.onError("HTTP " + response.code());
                    return;
                }
                try {
                    cb.onSuccess(response.body().string());
                } catch (Exception e) {
                    cb.onError(e.getMessage());
                } finally {
                    response.body().close();
                }
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
    @SuppressWarnings("UnusedReturnValue")
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
    @SuppressWarnings("UnusedReturnValue")
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
    @SuppressWarnings("UnusedReturnValue")
    public Call rewardPost(String token, long postId, String recvId, double amount, SimpleCallback cb) {
        String json = "{\"postId\":" + postId + ",\"recvId\":\"" + recvId + "\",\"amount\":" + amount + "}";
        simplePost("/v1/community/posts/post-reward", token, json, cb);
        return null;
    }

    // ==================== 评论 ====================

    /** 获取评论列表 */
    @SuppressWarnings("UnusedReturnValue")
    public Call getCommentList(String token, long postId, int page, int size, StringCallback cb) {
        String json = "{\"postId\":" + postId + ",\"size\":" + size + ",\"page\":" + page + "}";
        return post("/v1/community/comment/comment-list", token, json, cb);
    }

    /** 发表评论，parentCommentId=0 表示直接评论文章，否则为楼中楼回复 */
    @SuppressWarnings("UnusedReturnValue")
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
    @SuppressWarnings("UnusedReturnValue")
    public Call likeComment(String token, long commentId, SimpleCallback cb) {
        simplePost("/v1/community/comment/comment-like", token,
                "{\"id\":" + commentId + "}", cb);
        return null;
    }

    // ==================== 文章列表获取 API ====================

    /** 获取文章列表（POST /v1/community/posts/post-list） */
    @SuppressWarnings("UnusedReturnValue")
    public Call getPostList(String token, int typ, int baId, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            if (typ > 0) json.put("typ", typ);
            if (baId > 0) json.put("baId", baId);
            json.put("page", page);
            json.put("size", size);
            return post("/v1/community/posts/post-list", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 获取推荐文章列表（POST /v1/community/posts/post-list-recommend） */
    @SuppressWarnings("UnusedReturnValue")
    public Call getRecommendPostList(String token, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("page", page);
            json.put("size", size);
            return post("/v1/community/posts/post-list-recommend", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 转发文章到会话 (POST /v1/community/posts/post-forward) */
    @SuppressWarnings("UnusedReturnValue")
    public Call forwardPost(String token, long postId, String chatId, int chatType, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("postId", postId);

            org.json.JSONArray receiveArr = new org.json.JSONArray();
            JSONObject receiveObj = new JSONObject();
            receiveObj.put("chatId", chatId != null ? chatId : "");
            receiveObj.put("chatType", chatType);
            receiveArr.put(receiveObj);

            json.put("receive", receiveArr);

            return post("/v1/community/posts/post-forward", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    // ==================== 分区/板块 API ====================

    /** 获取分区列表（POST /v1/community/ba/following-ba-list） typ: 1-关注, 2-热门, 3-我的, 4-全部 */
    @SuppressWarnings("UnusedReturnValue")
    public Call getBaList(String token, int typ, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("typ", typ);
            json.put("page", page);
            json.put("size", size);
            return post("/v1/community/ba/following-ba-list", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 获取分区信息（POST /v1/community/ba/info） */
    @SuppressWarnings("UnusedReturnValue")
    public Call getBaInfo(String token, int baId, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("id", baId);
            return post("/v1/community/ba/info", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 关注分区 (POST /v1/community/ba/user-follow-ba) */
    @SuppressWarnings("UnusedReturnValue")
    public Call followBa(String token, int baId, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("baId", baId);
            json.put("followSource", 2);
            simplePost("/v1/community/ba/user-follow-ba", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
        return null;
    }

    /** 取关分区 (POST /v1/community/ba/user-unfollow-ba) */
    @SuppressWarnings("UnusedReturnValue")
    public Call unfollowBa(String token, int baId, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("baId", baId);
            simplePost("/v1/community/ba/user-unfollow-ba", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
        return null;
    }

    /** 获取分区下绑定的群聊列表（POST /v1/community/ba/group-list） */
    @SuppressWarnings("UnusedReturnValue")
    public Call getBaGroupList(String token, int baId, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("baId", baId);
            json.put("page", page);
            json.put("size", size);
            return post("/v1/community/ba/group-list", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 获取我的文章列表（POST /v1/community/posts/my-post-list） */
    @SuppressWarnings("UnusedReturnValue")
    public Call getMyPostList(String token, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("page", page);
            json.put("size", size);
            return post("/v1/community/posts/my-post-list", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 获取我的收藏文章列表（POST /v1/community/posts/post-collect-list） */
    @SuppressWarnings("UnusedReturnValue")
    public Call getMyCollectList(String token, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("page", page);
            json.put("size", size);
            return post("/v1/community/posts/post-collect-list", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 获取屏蔽用户列表（POST /v1/community/black-list） */
    @SuppressWarnings("UnusedReturnValue")
    public Call getBlackList(String token, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("page", page);
            json.put("size", size);
            return post("/v1/community/black-list", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /** 屏蔽/取消屏蔽用户（POST /v1/community/set-black-list） isAdd: 1-屏蔽, 0-取消屏蔽 */
    @SuppressWarnings("UnusedReturnValue")
    public Call setBlackList(String token, String authorId, int isAdd, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("authorId", authorId);
            json.put("isAdd", isAdd);
            simplePost("/v1/community/set-black-list", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
        return null;
    }
}
