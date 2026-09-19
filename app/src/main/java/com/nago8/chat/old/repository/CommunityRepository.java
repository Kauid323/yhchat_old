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

    /**
     * 发布文章 (POST /v1/community/posts/create)
     * @param token 用户 Token
     * @param baId 分区 ID
     * @param title 文章标题
     * @param content 文章内容
     * @param contentType 1: 纯文本, 2: Markdown
     * @param draftId 草稿 ID (若有则发布后自动删除草稿，没有传 0)
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call createPost(String token, int baId, String title, String content, int contentType, long draftId, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("baId", baId);
            json.put("groupId", "");
            json.put("title", title);
            json.put("content", content);
            json.put("contentType", contentType);
            json.put("draftId", draftId);
            return post("/v1/community/posts/create", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public Call createPost(String token, int baId, String title, String content, int contentType, StringCallback cb) {
        return createPost(token, baId, title, content, contentType, 0, cb);
    }

    /**
     * 保存/创建文章草稿 (POST /v1/community/posts/create-draft)
     * @param token 用户 Token
     * @param baId 分区 ID (可为 0)
     * @param title 草稿标题
     * @param content 草稿内容
     * @param contentType 1: 纯文本, 2: Markdown
     * @param draftId 已有草稿 ID (更新草稿时传入，新草稿传 0)
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call createDraft(String token, int baId, String title, String content, int contentType, long draftId, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("baId", baId);
            json.put("title", title);
            json.put("content", content);
            json.put("contentType", contentType);
            json.put("draftId", draftId);
            return post("/v1/community/posts/create-draft", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /**
     * 获取文章草稿信息 (POST /v1/community/posts/get-draft)
     * @param token 用户 Token
     * @param baId 分区 ID (一般为 0)
     * @param draftId 草稿 ID
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call getDraft(String token, int baId, long draftId, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("baId", baId);
            json.put("draftId", draftId);
            return post("/v1/community/posts/get-draft", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /**
     * 删除文章草稿 (POST /v1/community/posts/cancel-draft)
     * @param token 用户 Token
     * @param draftId 草稿 ID
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call cancelDraft(String token, long draftId, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("draftId", draftId);
            simplePost("/v1/community/posts/cancel-draft", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
        return null;
    }

    /**
     * 删除文章 (POST /v1/community/posts/delete)
     * @param token 用户 Token
     * @param postId 文章 ID
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call deletePost(String token, long postId, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("postId", postId);
            json.put("id", postId);
            simplePost("/v1/community/posts/delete", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
        return null;
    }

    /**
     * 编辑文章 (POST /v1/community/posts/edit)
     * @param token 用户 Token
     * @param postId 文章 ID
     * @param title 文章标题
     * @param content 文章内容
     * @param contentType 1: 纯文本, 2: Markdown
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call editPost(String token, long postId, String title, String content, int contentType, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("postId", postId);
            json.put("id", postId);
            json.put("title", title);
            json.put("content", content);
            json.put("contentType", contentType);
            return post("/v1/community/posts/edit", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }

    /**
     * 置顶/取消置顶文章 (POST /v1/community/posts/edit-sticky)
     * @param token 用户 Token
     * @param postId 文章 ID
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call editSticky(String token, long postId, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("postId", postId);
            json.put("id", postId);
            simplePost("/v1/community/posts/edit-sticky", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
        return null;
    }

    /**
     * 移动文章到其他板块 (POST /v1/community/posts/move)
     * @param token 用户 Token
     * @param postId 文章 ID
     * @param targetBaId 目标板块 ID
     * @param cb 回调
     */
    @SuppressWarnings("UnusedReturnValue")
    public Call movePost(String token, long postId, int targetBaId, SimpleCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("postId", postId);
            json.put("id", postId);
            json.put("baId", targetBaId);
            simplePost("/v1/community/posts/move", token, json.toString(), new SimpleCallback() {
                @Override
                public void onSuccess() {
                    cb.onSuccess();
                }

                @Override
                public void onError(String msg) {
                    // Fallback to move-post endpoint if move is not recognized
                    simplePost("/v1/community/posts/move-post", token, json.toString(), new SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            cb.onSuccess();
                        }

                        @Override
                        public void onError(String msg2) {
                            cb.onError(msg != null ? msg : msg2);
                        }
                    });
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
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

    /** 搜索社区分区与文章 (POST /v1/community/search) */
    @SuppressWarnings("UnusedReturnValue")
    public Call searchCommunity(String token, String keyword, int page, int size, StringCallback cb) {
        try {
            JSONObject json = new JSONObject();
            json.put("typ", 3);
            json.put("keyword", keyword != null ? keyword : "");
            json.put("page", page > 0 ? page : 1);
            json.put("size", size > 0 ? size : 50);
            return post("/v1/community/search", token, json.toString(), cb);
        } catch (Exception e) {
            cb.onError(e.getMessage());
            return null;
        }
    }
}
