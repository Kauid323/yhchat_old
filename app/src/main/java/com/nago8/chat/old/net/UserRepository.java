package com.nago8.chat.old.net;

import com.nago8.chat.old.proto.user.get_user;
import com.nago8.chat.old.proto.user.get_user_send;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UserRepository {

    public interface GetUserCallback {
        void onSuccess(get_user response);

        void onError(Exception error);
    }

    public Call getUser(String token, String userId, GetUserCallback callback) {
        if (userId == null || userId.length() == 0) {
            callback.onError(new IllegalArgumentException("userId is empty"));
            return null;
        }

        get_user_send requestProto = new get_user_send.Builder()
                .id(userId)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/user/get-user")
                .header("token", token == null ? "" : token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }
                    callback.onSuccess(get_user.ADAPTER.decode(response.body().source()));
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
