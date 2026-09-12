package com.nago8.chat.old.repository;

import androidx.annotation.NonNull;

import com.nago8.chat.old.model.Expression;
import com.nago8.chat.old.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ExpressionRepository {

    public interface ExpressionsCallback {
        void onSuccess(List<Expression> expressions);
        void onError(Exception error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(Exception error);
    }

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * 获取个人表情收藏列表 (POST /v1/expression/list)
     */
    public Call listExpressions(String token, ExpressionsCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        RequestBody body = RequestBody.create(JSON_TYPE, "{}");
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/expression/list")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code != 1) {
                        String msg = root.optString("msg", "Failed to load expressions");
                        callback.onError(new Exception(msg));
                        return;
                    }

                    List<Expression> list = new ArrayList<>();
                    JSONObject data = root.optJSONObject("data");
                    JSONArray arr = null;
                    if (data != null) {
                        arr = data.optJSONArray("expression");
                        if (arr == null) arr = data.optJSONArray("expressions");
                        if (arr == null) arr = data.optJSONArray("list");
                    } else {
                        arr = root.optJSONArray("data");
                    }

                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.optJSONObject(i);
                            if (obj != null) {
                                Expression exp = new Expression();
                                exp.id = obj.optLong("id", 0);
                                exp.url = obj.optString("url", "");
                                exp.urlOriginal = obj.optString("urlOriginal", obj.optString("url_original", ""));
                                exp.delFlag = obj.optInt("delFlag", obj.optInt("del_flag", 0));
                                exp.createTime = obj.optLong("createTime", obj.optLong("create_time", 0));
                                exp.createBy = obj.optString("createBy", obj.optString("create_by", ""));
                                list.add(exp);
                            }
                        }
                    }
                    callback.onSuccess(list);
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }

    /**
     * 添加图片到个人表情收藏 (POST /v1/expression/create)
     */
    public Call addExpression(String token, String imageUrl, SimpleCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        JSONObject req = new JSONObject();
        try {
            req.put("url", imageUrl);
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(JSON_TYPE, req.toString());
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/expression/create")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code == 1 || code == 200) {
                        callback.onSuccess();
                    } else {
                        String msg = root.optString("msg", root.optString("message", "Add expression failed"));
                        callback.onError(new Exception(msg));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }

    /**
     * 删除个人表情收藏 (POST /v1/expression/delete)
     */
    public Call deleteExpression(String token, long expressionId, SimpleCallback callback) {
        JSONObject req = new JSONObject();
        try {
            req.put("id", expressionId);
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(JSON_TYPE, req.toString());
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/expression/delete")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code == 1 || code == 200) {
                        callback.onSuccess();
                    } else {
                        String msg = root.optString("msg", root.optString("message", "Delete expression failed"));
                        callback.onError(new Exception(msg));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }

    /**
     * 置顶个人表情收藏 (POST /v1/expression/topping)
     */
    public Call topExpression(String token, long expressionId, SimpleCallback callback) {
        JSONObject req = new JSONObject();
        try {
            req.put("id", expressionId);
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(JSON_TYPE, req.toString());
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/expression/topping")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code == 1 || code == 200) {
                        callback.onSuccess();
                    } else {
                        String msg = root.optString("msg", root.optString("message", "Top expression failed"));
                        callback.onError(new Exception(msg));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }
}
