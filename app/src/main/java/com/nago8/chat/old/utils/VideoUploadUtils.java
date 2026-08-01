package com.nago8.chat.old.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VideoUploadUtils {

    private static final String TAG = "VideoUploadUtils";
    private static final String VIDEO_BUCKET = "chat68-video";
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024L; // 500MB

    public static class QiniuVideoResult {
        public String key;
        public String hash;
        public long fsize;
        public int width;
        public int height;
        public String fileName;
        public String fileExtension;
        public String mimeType;
    }

    public interface TokenCallback {
        void onSuccess(String uploadToken);
        void onError(Exception e);
    }

    public interface UploadCallback {
        void onSuccess(QiniuVideoResult result);
        void onError(Exception e);
    }

    /**
     * 获取七牛云视频上传 Token (/v1/misc/qiniu-token-video)
     */
    public static void getQiniuVideoUploadToken(String token, TokenCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return;
        }

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/misc/qiniu-token-video")
                .header("token", token)
                .header("Content-Type", "application/json")
                .get()
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(new IOException("HTTP " + response.code()));
                    return;
                }

                try {
                    String jsonStr = response.body().string();
                    JSONObject json = new JSONObject(jsonStr);
                    int code = json.optInt("code", 0);
                    if (code == 1) {
                        JSONObject data = json.optJSONObject("data");
                        String qiniuToken = data != null ? data.optString("token", "") : "";
                        if (!qiniuToken.isEmpty()) {
                            callback.onSuccess(qiniuToken);
                        } else {
                            callback.onError(new Exception("qiniu video token is empty"));
                        }
                    } else {
                        String msg = json.optString("msg", "获取视频上传token失败");
                        callback.onError(new Exception(msg));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    response.body().close();
                }
            }
        });
    }

    /**
     * 上传单个视频到七牛云 (bucket: chat68-video)
     */
    public static void uploadVideo(Context context, Uri videoUri, String uploadToken, UploadCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                String originalFileName = getFileName(context, videoUri);
                if (originalFileName == null || originalFileName.isEmpty()) {
                    originalFileName = "video_" + System.currentTimeMillis() + ".mp4";
                }

                String extension = getFileExtension(originalFileName);
                String mimeType = context.getContentResolver().getType(videoUri);
                if (mimeType == null) {
                    mimeType = getMimeTypeFromExtension(extension);
                }
                if (mimeType == null) {
                    mimeType = "video/mp4";
                }

                long fileSizeBytes = getFileSize(context, videoUri);
                if (fileSizeBytes > MAX_FILE_SIZE) {
                    callback.onError(new Exception("视频文件过大，请选择小于500MB的视频"));
                    return;
                }

                tempFile = new File(context.getCacheDir(), "temp_video_" + System.currentTimeMillis() + "." + extension);

                InputStream inputStream = context.getContentResolver().openInputStream(videoUri);
                if (inputStream == null) {
                    callback.onError(new Exception("无法读取视频输入流"));
                    return;
                }

                String md5 = copyToTempFileAndCalculateMD5(inputStream, tempFile);
                long fsize = tempFile.length();

                String videoKey = md5 + "." + extension;

                String ak = uploadToken.split(":")[0];
                String queryUrl = "https://api.qiniu.com/v4/query?ak=" + ak + "&bucket=" + VIDEO_BUCKET;

                Request queryReq = new Request.Builder().url(queryUrl).get().build();
                Response queryResp = ApiClient.getClient().newCall(queryReq).execute();

                String uploadHost = "upload-cn-east-2.qiniup.com";
                if (queryResp.isSuccessful() && queryResp.body() != null) {
                    try {
                        String qJson = queryResp.body().string();
                        JSONObject jobj = new JSONObject(qJson);
                        JSONArray hosts = jobj.getJSONArray("hosts");
                        JSONObject host0 = hosts.getJSONObject(0);
                        JSONObject up = host0.getJSONObject("up");
                        JSONArray domains = up.getJSONArray("domains");
                        uploadHost = domains.getString(0);
                    } catch (Exception ignored) {
                    }
                }
                if (queryResp.body() != null) queryResp.body().close();

                RequestBody formBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("token", uploadToken)
                        .addFormDataPart("key", videoKey)
                        .addFormDataPart("file", originalFileName, RequestBody.create(MediaType.parse(mimeType), tempFile))
                        .build();

                Request uploadReq = new Request.Builder()
                        .url("https://" + uploadHost)
                        .addHeader("user-agent", "QiniuDart")
                        .addHeader("accept-encoding", "gzip")
                        .post(formBody)
                        .build();

                Response uploadResp = ApiClient.getClient().newCall(uploadReq).execute();
                if (uploadResp.isSuccessful() && uploadResp.body() != null) {
                    String respStr = uploadResp.body().string();
                    JSONObject json = new JSONObject(respStr);

                    QiniuVideoResult res = new QiniuVideoResult();
                    res.key = json.optString("key", videoKey);
                    res.hash = json.optString("hash", md5);
                    res.fsize = json.optLong("fsize", fsize);
                    res.fileName = originalFileName;
                    res.fileExtension = extension;
                    res.mimeType = mimeType;

                    if (json.has("avinfo")) {
                        try {
                            JSONObject avinfo = json.getJSONObject("avinfo");
                            if (avinfo.has("video")) {
                                JSONObject v = avinfo.getJSONObject("video");
                                res.width = v.optInt("width", 0);
                                res.height = v.optInt("height", 0);
                            }
                        } catch (Exception ignored) {}
                    }

                    callback.onSuccess(res);
                } else {
                    int errCode = uploadResp.code();
                    callback.onError(new Exception("视频上传失败 HTTP " + errCode));
                }
                if (uploadResp.body() != null) uploadResp.body().close();

            } catch (Exception e) {
                Log.e(TAG, "uploadVideo error", e);
                callback.onError(e);
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        }).start();
    }

    private static String copyToTempFileAndCalculateMD5(InputStream inputStream, File tempFile) throws Exception {
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        try (InputStream input = inputStream; FileOutputStream output = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                md5Digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.flush();
        }

        byte[] digest = md5Digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String getFileName(Context context, Uri uri) {
        String fileName = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "failed to get fileName from ContentResolver", e);
            }
        }

        if (fileName == null && uri.getPath() != null) {
            String path = uri.getPath();
            int cut = path.lastIndexOf('/');
            if (cut != -1) {
                fileName = path.substring(cut + 1);
            } else {
                fileName = path;
            }
        }
        return fileName;
    }

    private static long getFileSize(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {}

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) return is.available();
        } catch (Exception ignored) {}

        return 0L;
    }

    private static String getFileExtension(String fileName) {
        if (fileName == null) return "mp4";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "mp4";
    }

    private static String getMimeTypeFromExtension(String extension) {
        if (extension == null) return null;
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
    }
}
