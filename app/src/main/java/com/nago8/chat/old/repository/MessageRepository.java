package com.nago8.chat.old.repository;

import android.content.Context;
import android.net.Uri;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.list_message;
import com.nago8.chat.old.proto.list_message_by_seq;
import com.nago8.chat.old.proto.list_message_by_seq_send;
import com.nago8.chat.old.proto.list_message_send;
import com.nago8.chat.old.proto.send_message;
import com.nago8.chat.old.proto.send_message_send;
import com.nago8.chat.old.utils.ImageUploadUtils;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageRepository {

    public interface ImageUploadListener {
        void onProgress(int index, int total);
        void onImageSuccess(int index, int total);
        void onImageError(int index, int total, Exception error);
        void onAllCompleted();
    }

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

    public Call sendImageMessage(String token, String chatId, int chatType,
                                 String fileKey, String hash, long fsize,
                                 int width, int height, String extension,
                                 SendMessageCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (chatId == null || chatId.isEmpty()) {
            callback.onError(new IllegalArgumentException("chatId is empty"));
            return null;
        }

        String msgId = UUID.randomUUID().toString().replace("-", "");

        send_message_send.Content content = new send_message_send.Content.Builder()
                .image(fileKey)
                .file_size(fsize)
                .build();

        send_message_send.Media media = new send_message_send.Media.Builder()
                .file_key(fileKey)
                .file_key2(fileKey)
                .file_hash(hash != null ? hash : "")
                .file_type("image/" + (extension != null ? extension : "jpeg"))
                .file_size(fsize)
                .image_width((long) width)
                .image_height((long) height)
                .file_suffix(extension != null ? extension : "jpg")
                .build();

        send_message_send requestProto = new send_message_send.Builder()
                .msg_id(msgId)
                .chat_id(chatId)
                .chat_type((long) chatType)
                .content(content)
                .media(media)
                .content_type(2L)
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

    public void uploadAndSendImages(Context context, String token, String chatId, int chatType,
                                     List<Uri> uris, ImageUploadListener listener) {
        if (token == null || token.isEmpty()) {
            if (listener != null) listener.onImageError(0, uris != null ? uris.size() : 0, new IllegalArgumentException("用户未登录"));
            return;
        }
        if (uris == null || uris.isEmpty()) return;

        ImageUploadUtils.getQiniuUploadToken(token, new ImageUploadUtils.TokenCallback() {
            @Override
            public void onSuccess(String uploadToken) {
                final int total = uris.size();
                for (int i = 0; i < total; i++) {
                    Uri imageUri = uris.get(i);
                    final int index = i + 1;
                    if (listener != null) listener.onProgress(index, total);

                    ImageUploadUtils.uploadImage(context, imageUri, uploadToken, new ImageUploadUtils.UploadCallback() {
                        @Override
                        public void onSuccess(ImageUploadUtils.QiniuResult res) {
                            sendImageMessage(token, chatId, chatType,
                                    res.key, res.hash, res.fsize, res.width, res.height, res.extension,
                                    new SendMessageCallback() {
                                        @Override
                                        public void onSuccess(send_message response) {
                                            if (listener != null) {
                                                listener.onImageSuccess(index, total);
                                                if (index == total) listener.onAllCompleted();
                                            }
                                        }

                                        @Override
                                        public void onError(Exception error) {
                                            if (listener != null) listener.onImageError(index, total, error);
                                        }
                                    });
                        }

                        @Override
                        public void onError(Exception e) {
                            if (listener != null) listener.onImageError(index, total, e);
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (listener != null) listener.onImageError(0, uris.size(), e);
            }
        });
    }
}
