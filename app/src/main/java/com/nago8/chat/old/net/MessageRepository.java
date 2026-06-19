package com.nago8.chat.old.net;

import com.nago8.chat.old.proto.list_message;
import com.nago8.chat.old.proto.list_message_by_seq;
import com.nago8.chat.old.proto.list_message_by_seq_send;
import com.nago8.chat.old.proto.list_message_send;
import com.nago8.chat.old.proto.send_message;
import com.nago8.chat.old.proto.send_message_send;
import java.util.UUID;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageRepository {

    public interface MessageListCallback {
        void onSuccess(list_message_by_seq response);

        void onError(Exception error);
    }

    public interface OlderMessageListCallback {
        void onSuccess(list_message response);

        void onError(Exception error);
    }

    public interface SendMessageCallback {
        void onSuccess(send_message response);

        void onError(Exception error);
    }

    public Call listMessageBySeq(String token, String chatId, int chatType, long msgSeq, MessageListCallback callback) {
        if (token == null || token.length() == 0) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (chatId == null || chatId.length() == 0) {
            callback.onError(new IllegalArgumentException("chatId is empty"));
            return null;
        }

        list_message_by_seq_send requestProto = new list_message_by_seq_send.Builder()
                .chat_id(chatId)
                .chat_type(chatType)
                .msg_seq(msgSeq)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/msg/list-message-by-seq")
                .header("token", token)
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
                    callback.onSuccess(list_message_by_seq.ADAPTER.decode(response.body().source()));
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

    public Call listMessage(String token, String chatId, int chatType, String msgId, long msgCount, OlderMessageListCallback callback) {
        if (token == null || token.length() == 0) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (chatId == null || chatId.length() == 0) {
            callback.onError(new IllegalArgumentException("chatId is empty"));
            return null;
        }
        if (msgId == null || msgId.length() == 0) {
            callback.onError(new IllegalArgumentException("msgId is empty"));
            return null;
        }

        list_message_send requestProto = new list_message_send.Builder()
                .chat_id(chatId)
                .chat_type(chatType)
                .msg_id(msgId)
                .msg_count(msgCount)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/msg/list-message")
                .header("token", token)
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
                    callback.onSuccess(list_message.ADAPTER.decode(response.body().source()));
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

    public Call sendMessage(String token, String chatId, int chatType, String text, SendMessageCallback callback) {
        if (token == null || token.length() == 0) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (chatId == null || chatId.length() == 0) {
            callback.onError(new IllegalArgumentException("chatId is empty"));
            return null;
        }
        if (text == null || text.length() == 0) {
            callback.onError(new IllegalArgumentException("text is empty"));
            return null;
        }

        String msgId = UUID.randomUUID().toString().replace("-", "");

        send_message_send requestProto = new send_message_send.Builder()
                .msg_id(msgId)
                .chat_id(chatId)
                .chat_type(chatType)
                .content(new send_message_send.Content.Builder()
                        .text(text)
                        .build())
                .content_type(1L)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/msg/send-message")
                .header("token", token)
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
                    callback.onSuccess(send_message.ADAPTER.decode(response.body().source()));
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
