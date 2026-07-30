package com.nago8.chat.old.repository;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.proto.group.info_send;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroupRepository {

    public interface GroupInfoCallback {
        void onSuccess(info response);
        void onError(Exception error);
    }

    public Call getGroupInfo(String token, String groupId, GroupInfoCallback callback) {
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (groupId == null || groupId.isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("groupId is empty"));
            return null;
        }

        info_send requestProto = new info_send.Builder()
                .group_id(groupId)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/info")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        if (callback != null) callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }
                    if (callback != null) {
                        callback.onSuccess(info.ADAPTER.decode(response.body().source()));
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
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
