package com.nago8.chat.old.repository;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.list_message;
import com.nago8.chat.old.proto.list_message_by_seq;
import com.nago8.chat.old.proto.list_message_by_seq_send;
import com.nago8.chat.old.proto.list_message_send;
import com.nago8.chat.old.proto.recall_msg;
import com.nago8.chat.old.proto.recall_msg_send;
import com.nago8.chat.old.proto.send_message;
import com.nago8.chat.old.proto.send_message_send;
import com.nago8.chat.old.utils.FileUploadUtils;
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

    public interface FileUploadListener {
        void onProgress(int index, int total);
        void onFileSuccess(int index, int total, String fileName);
        void onFileError(int index, int total, Exception error);
        void onAllCompleted();
    }

    public interface VideoUploadListener {
        void onProgress(int index, int total);
        void onVideoSuccess(int index, int total, String fileName);
        void onVideoError(int index, int total, Exception error);
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

    public interface RecallMessageCallback {
        void onSuccess();
        void onError(String errorMsg);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Call listMessageBySeq(String token, String chatId, int chatType, long msgSeq, MessageListCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (chatId == null || chatId.isEmpty()) {
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

    @SuppressWarnings("UnusedReturnValue")
    public Call listMessage(String token, String chatId, int chatType, String msgId, long msgCount, OlderMessageListCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (chatId == null || chatId.isEmpty()) {
            callback.onError(new IllegalArgumentException("chatId is empty"));
            return null;
        }
        if (msgId == null || msgId.isEmpty()) {
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

    @SuppressWarnings("UnusedReturnValue")
    public Call sendMessage(String token, String chatId, int chatType, String text, SendMessageCallback callback) {
        return sendMessage(token, chatId, chatType, text, null, null, callback);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Call sendMessage(String token, String chatId, int chatType, String text,
                            String quoteId, String quoteText, SendMessageCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }
        if (chatId == null || chatId.isEmpty()) {
            callback.onError(new IllegalArgumentException("chatId is empty"));
            return null;
        }
        if (text == null || text.isEmpty()) {
            callback.onError(new IllegalArgumentException("text is empty"));
            return null;
        }

        String msgId = UUID.randomUUID().toString().replace("-", "");

        send_message_send.Content.Builder contentBuilder = new send_message_send.Content.Builder()
                .text(text);
        if (quoteText != null && !quoteText.isEmpty()) {
            contentBuilder.quote_msg_text(quoteText);
        }

        send_message_send.Builder msgBuilder = new send_message_send.Builder()
                .msg_id(msgId)
                .chat_id(chatId)
                .chat_type(chatType)
                .content(contentBuilder.build())
                .content_type(1);
        if (quoteId != null && !quoteId.isEmpty()) {
            msgBuilder.quote_msg_id(quoteId);
        }
        send_message_send requestProto = msgBuilder.build();

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

    @SuppressWarnings("UnusedReturnValue")
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
                .image_width(width)
                .image_height(height)
                .file_suffix(extension != null ? extension : "jpg")
                .build();

        send_message_send requestProto = new send_message_send.Builder()
                .msg_id(msgId)
                .chat_id(chatId)
                .chat_type(chatType)
                .content(content)
                .media(media)
                .content_type(2)
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

    @SuppressWarnings("UnusedReturnValue")
    public Call sendFileMessage(String token, String chatId, int chatType,
                                String fileName, String fileKey, String fileHash,
                                long fileSize, String mimeType, String fileExtension,
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
                .file_name(fileName != null ? fileName : "")
                .file(fileKey)
                .file_size(fileSize)
                .build();

        send_message_send.Media media = new send_message_send.Media.Builder()
                .file_key(fileKey)
                .file_key2(fileKey)
                .file_hash(fileHash != null ? fileHash : "")
                .file_type(mimeType != null ? mimeType : "application/octet-stream")
                .file_size(fileSize)
                .file_suffix(fileExtension != null ? fileExtension : "dat")
                .build();

        send_message_send requestProto = new send_message_send.Builder()
                .msg_id(msgId)
                .chat_id(chatId)
                .chat_type(chatType)
                .content(content)
                .media(media)
                .content_type(4) // 4 = 文件
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

    public void uploadAndSendFiles(Context context, String token, String chatId, int chatType,
                                   List<Uri> uris, FileUploadListener listener) {
        if (token == null || token.isEmpty()) {
            if (listener != null) listener.onFileError(0, uris != null ? uris.size() : 0, new IllegalArgumentException("用户未登录"));
            return;
        }
        if (uris == null || uris.isEmpty()) return;

        FileUploadUtils.getQiniuFileUploadToken(token, new FileUploadUtils.TokenCallback() {
            @Override
            public void onSuccess(String uploadToken) {
                final int total = uris.size();
                for (int i = 0; i < total; i++) {
                    Uri fileUri = uris.get(i);
                    final int index = i + 1;
                    if (listener != null) listener.onProgress(index, total);

                    FileUploadUtils.uploadFile(context, fileUri, uploadToken, new FileUploadUtils.UploadCallback() {
                        @Override
                        public void onSuccess(FileUploadUtils.QiniuFileResult res) {
                            sendFileMessage(token, chatId, chatType,
                                    res.fileName, res.key, res.hash, res.fsize, res.mimeType, res.fileExtension,
                                    new SendMessageCallback() {
                                        @Override
                                        public void onSuccess(send_message response) {
                                            if (listener != null) {
                                                listener.onFileSuccess(index, total, res.fileName);
                                                if (index == total) listener.onAllCompleted();
                                            }
                                        }

                                        @Override
                                        public void onError(Exception error) {
                                            if (listener != null) listener.onFileError(index, total, error);
                                        }
                                    });
                        }

                        @Override
                        public void onError(Exception e) {
                            if (listener != null) listener.onFileError(index, total, e);
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (listener != null) listener.onFileError(0, uris.size(), e);
            }
        });
    }

    @SuppressWarnings("UnusedReturnValue")
    public Call sendVideoMessage(String token, String chatId, int chatType,
                                 String fileName, String fileKey, String fileHash,
                                 long fileSize, int width, int height, String mimeType, String fileExtension,
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
                .file_name(fileName != null ? fileName : "")
                .video(fileKey)
                .file_size(fileSize)
                .build();

        send_message_send.Media media = new send_message_send.Media.Builder()
                .file_key(fileKey)
                .file_key2(fileKey)
                .file_hash(fileHash != null ? fileHash : "")
                .file_type(mimeType != null ? mimeType : "video/mp4")
                .image_width((long) width)
                .image_height((long) height)
                .file_size(fileSize)
                .file_suffix(fileExtension != null ? fileExtension : "mp4")
                .build();

        send_message_send requestProto = new send_message_send.Builder()
                .msg_id(msgId)
                .chat_id(chatId)
                .chat_type((long) chatType)
                .content(content)
                .media(media)
                .content_type(10) // 10 = 视频
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

    public void uploadAndSendVideos(Context context, String token, String chatId, int chatType,
                                    List<Uri> uris, VideoUploadListener listener) {
        if (token == null || token.isEmpty()) {
            if (listener != null) listener.onVideoError(0, uris != null ? uris.size() : 0, new IllegalArgumentException("用户未登录"));
            return;
        }
        if (uris == null || uris.isEmpty()) return;

        com.nago8.chat.old.utils.VideoUploadUtils.getQiniuVideoUploadToken(token, new com.nago8.chat.old.utils.VideoUploadUtils.TokenCallback() {
            @Override
            public void onSuccess(String uploadToken) {
                final int total = uris.size();
                for (int i = 0; i < total; i++) {
                    Uri videoUri = uris.get(i);
                    final int index = i + 1;
                    if (listener != null) listener.onProgress(index, total);

                    com.nago8.chat.old.utils.VideoUploadUtils.uploadVideo(context, videoUri, uploadToken, new com.nago8.chat.old.utils.VideoUploadUtils.UploadCallback() {
                        @Override
                        public void onSuccess(com.nago8.chat.old.utils.VideoUploadUtils.QiniuVideoResult res) {
                            sendVideoMessage(token, chatId, chatType,
                                    res.fileName, res.key, res.hash, res.fsize, res.width, res.height, res.mimeType, res.fileExtension,
                                    new SendMessageCallback() {
                                        @Override
                                        public void onSuccess(send_message response) {
                                            if (listener != null) {
                                                listener.onVideoSuccess(index, total, res.fileName);
                                                if (index == total) listener.onAllCompleted();
                                            }
                                        }

                                        @Override
                                        public void onError(Exception error) {
                                            if (listener != null) listener.onVideoError(index, total, error);
                                        }
                                    });
                        }

                        @Override
                        public void onError(Exception e) {
                            if (listener != null) listener.onVideoError(index, total, e);
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (listener != null) listener.onVideoError(0, uris.size(), e);
            }
        });
    }

    /** 撤回消息 (POST /v1/msg/recall-msg) */
    @SuppressWarnings("UnusedReturnValue")
    public Call recallMessage(String token, String msgId, String chatId, int chatType, RecallMessageCallback callback) {
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("token is empty");
            return null;
        }

        recall_msg_send requestProto = new recall_msg_send.Builder()
                .msg_id(msgId != null ? msgId : "")
                .chat_id(chatId != null ? chatId : "")
                .chat_type((long) chatType)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/msg/recall-msg")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        recall_msg result = recall_msg.ADAPTER.decode(response.body().source());
                        if (result != null && result.status != null && result.status.code == 1) {
                            if (callback != null) callback.onSuccess();
                        } else {
                            String msg = (result != null && result.status != null && result.status.msg != null) ? result.status.msg : "撤回失败";
                            if (callback != null) callback.onError(msg);
                        }
                    } catch (Exception e) {
                        if (callback != null) callback.onError(e.getMessage());
                    } finally {
                        response.body().close();
                    }
                } else {
                    if (callback != null) callback.onError("HTTP " + response.code());
                }
            }
        });
        return call;
    }
}
