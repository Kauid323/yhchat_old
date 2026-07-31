package com.nago8.chat.old.repository;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

@SuppressWarnings("UnusedReturnValue")
public class GroupRepository {

    public interface GroupInfoCallback {
        void onSuccess(info response);
        void onError(Exception error);
    }

    public interface GroupActionCallback {
        void onSuccess(int code, String msg);
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

    /**
     * 踢出群成员 (POST /v1/group/remove-member)
     */
    public Call removeMember(String token, String groupId, String userId, GroupActionCallback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("groupId", groupId != null ? groupId : "");
        json.addProperty("userId", userId != null ? userId : "");

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/remove-member")
                .header("token", token == null ? "" : token)
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
                handleJsonResponse(response, callback);
            }
        });
        return call;
    }

    /**
     * 禁言群成员 (POST /v1/group/gag-member)
     */
    public Call gagMember(String token, String groupId, String userId, int gagSeconds, GroupActionCallback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("groupId", groupId != null ? groupId : "");
        json.addProperty("userId", userId != null ? userId : "");
        json.addProperty("gag", gagSeconds);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/gag-member")
                .header("token", token == null ? "" : token)
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
                handleJsonResponse(response, callback);
            }
        });
        return call;
    }

    /**
     * 设置/移除群管理员 (POST /v1/group/edit-admin)
     */
    public Call editAdmin(String token, String groupId, String userId, boolean setAdmin, GroupActionCallback callback) {
        JsonObject json = new JsonObject();
        json.addProperty("groupId", groupId != null ? groupId : "");
        json.addProperty("userId", userId != null ? userId : "");
        json.addProperty("admin", setAdmin ? 1 : 0);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/edit-admin")
                .header("token", token == null ? "" : token)
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
                handleJsonResponse(response, callback);
            }
        });
        return call;
    }

    /**
     * 创建群聊 (POST /v1/group/create-group)
     */
    public Call createGroup(String token, String name, String introduction, String avatarUrl, GroupActionCallback callback) {
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        com.nago8.chat.old.proto.group.create_group_send requestProto =
                new com.nago8.chat.old.proto.group.create_group_send.Builder()
                        .name(name != null ? name : "")
                        .introduction(introduction != null ? introduction : "")
                        .avatar_url(avatarUrl != null ? avatarUrl : "")
                        .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/create-group")
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
                    com.nago8.chat.old.proto.group.create_group resultProto = com.nago8.chat.old.proto.group.create_group.ADAPTER.decode(response.body().source());
                    int code = resultProto.status != null ? resultProto.status.code : -1;
                    String msg = (resultProto.status != null && resultProto.status.msg != null) ? resultProto.status.msg : "";
                    if (callback != null) callback.onSuccess(code, !msg.isEmpty() ? msg : resultProto.group_id);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                } finally {
                    if (response.body() != null) response.body().close();
                }
            }
        });
        return call;
    }

    private void handleJsonResponse(Response response, GroupActionCallback callback) {
        try {
            if (!response.isSuccessful() || response.body() == null) {
                if (callback != null) callback.onError(new IOException("HTTP " + response.code()));
                return;
            }
            String respStr = response.body().string();
            JsonObject root = JsonParser.parseString(respStr).getAsJsonObject();
            int code = root.has("code") ? root.get("code").getAsInt() : (root.has("status") && root.getAsJsonObject("status").has("code") ? root.getAsJsonObject("status").get("code").getAsInt() : -1);
            String msg = root.has("msg") && !root.get("msg").isJsonNull() ? root.get("msg").getAsString() : "";
            if (callback != null) {
                callback.onSuccess(code, msg);
            }
        } catch (Exception e) {
            if (callback != null) callback.onError(e);
        } finally {
            if (response.body() != null) {
                response.body().close();
            }
        }
    }
}
