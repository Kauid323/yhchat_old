package com.nago8.chat.old.repository;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.bot.create_bot;
import com.nago8.chat.old.proto.bot.create_bot_send;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("UnusedReturnValue")
public class BotRepository {

    public interface BotActionCallback {
        void onSuccess(int code, String msg, String botId);
        void onError(Exception error);
    }

    /**
     * 创建机器人 (POST /v1/bot/create-bot)
     */
    public Call createBot(String token, String name, String introduction, String avatarUrl, boolean isPrivate, BotActionCallback callback) {
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        create_bot_send requestProto = new create_bot_send.Builder()
                .name(name != null ? name : "")
                .introduction(introduction != null ? introduction : "")
                .avatar_url(avatarUrl != null ? avatarUrl : "")
                .private_(isPrivate)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/bot/create-bot")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        if (callback != null) callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }
                    create_bot resultProto = create_bot.ADAPTER.decode(response.body().source());
                    int code = resultProto.status != null ? resultProto.status.code : -1;
                    String msg = (resultProto.status != null && resultProto.status.msg != null) ? resultProto.status.msg : "";
                    String botId = (resultProto.data != null && resultProto.data.bot_id != null) ? resultProto.data.bot_id : "";
                    if (callback != null) callback.onSuccess(code, msg, botId);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                } finally {
                    if (response.body() != null) response.body().close();
                }
            }
        });
        return call;
    }
}
