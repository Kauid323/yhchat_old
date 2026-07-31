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

public class ReportRepository {

    public interface ReportCallback {
        void onSuccess(int code, String msg);
        void onError(Exception error);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Call submitReport(String token, String chatId, int chatType, String chatName, String content, String imageUrl, String reason, ReportCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("chatId", chatId != null ? chatId : "");
            json.put("chatType", chatType);
            json.put("chatName", chatName != null ? chatName : "");
            json.put("content", content != null ? content : "");
            json.put("url", imageUrl != null ? imageUrl : "");
            json.put("reason", reason != null ? reason : "");

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    json.toString()
            );

            Request request = new Request.Builder()
                    .url(ApiClient.BASE_URL + "/v1/report/create")
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
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }
                    try {
                        String respStr = response.body().string();
                        JSONObject resJson = new JSONObject(respStr);
                        int code = resJson.optInt("code", 0);
                        String msg = resJson.optString("msg", "");
                        callback.onSuccess(code, msg);
                    } catch (Exception e) {
                        callback.onError(e);
                    } finally {
                        response.body().close();
                    }
                }
            });
            return call;
        } catch (Exception e) {
            callback.onError(e);
            return null;
        }
    }
}
