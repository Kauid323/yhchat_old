package com.nago8.chat.old.utils;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
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

public class ImageUploadUtils {

    private static final String TAG = "ImageUploadUtils";
    private static final String IMAGE_BUCKET = "chat68";

    public static class QiniuResult {
        public String key;
        public String hash;
        public long fsize;
        public int width;
        public int height;
        public String extension;
    }

    public interface TokenCallback {
        void onSuccess(String uploadToken);
        void onError(Exception e);
    }

    public interface UploadCallback {
        void onSuccess(QiniuResult result);
        void onError(Exception e);
    }

    /**
     * 获取七牛云上传 Token (/v1/misc/qiniu-token)
     */
    public static void getQiniuUploadToken(String token, TokenCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return;
        }

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/misc/qiniu-token")
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
                            callback.onError(new Exception("qiniu token is empty"));
                        }
                    } else {
                        String msg = json.optString("msg", "获取token失败");
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
     * 上传单张图片到七牛云
     */
    public static void uploadImage(Context context, Uri imageUri, String uploadToken, UploadCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                String mimeType = context.getContentResolver().getType(imageUri);
                if (mimeType == null) mimeType = "image/jpeg";

                String extension = getExtensionFromMime(mimeType);

                tempFile = new File(context.getCacheDir(), "img_up_" + System.currentTimeMillis() + "_" + imageUri.hashCode() + "." + extension);

                InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    callback.onError(new Exception("无法读取图片输入流"));
                    return;
                }

                String md5 = copyToTempFileAndCalculateMD5(inputStream, tempFile);
                int[] bounds = decodeImageBounds(tempFile);
                int width = bounds[0];
                int height = bounds[1];
                long fsize = tempFile.length();

                String fileKey = md5 + "." + extension;

                String ak = uploadToken.split(":")[0];
                String queryUrl = "https://api.qiniu.com/v4/query?ak=" + ak + "&bucket=" + IMAGE_BUCKET;

                Request queryReq = new Request.Builder().url(queryUrl).get().build();
                Response queryResp = ApiClient.getClient().newCall(queryReq).execute();

                String uploadHost = "upload-z2.qiniup.com";
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
                        .addFormDataPart("key", fileKey)
                        .addFormDataPart("file", fileKey, RequestBody.create(MediaType.parse(mimeType), tempFile))
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

                    QiniuResult res = new QiniuResult();
                    res.key = json.optString("key", fileKey);
                    res.hash = json.optString("hash", "");
                    res.fsize = json.optLong("fsize", fsize);
                    res.width = width;
                    res.height = height;
                    res.extension = extension;

                    if (json.has("avinfo")) {
                        try {
                            JSONObject avinfo = json.getJSONObject("avinfo");
                            if (avinfo.has("video")) {
                                JSONObject v = avinfo.getJSONObject("video");
                                res.width = v.optInt("width", width);
                                res.height = v.optInt("height", height);
                            }
                        } catch (Exception ignored) {}
                    }

                    callback.onSuccess(res);
                } else {
                    int errCode = uploadResp.code();
                    callback.onError(new Exception("上传失败 HTTP " + errCode));
                }
                if (uploadResp.body() != null) uploadResp.body().close();

            } catch (Exception e) {
                Log.e(TAG, "uploadImage failed", e);
                callback.onError(e);
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }).start();
    }

    private static String getExtensionFromMime(String mimeType) {
        if (mimeType == null) return "jpg";
        switch (mimeType) {
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "image/bmp": return "bmp";
            case "image/webp": return "webp";
            default: return "jpg";
        }
    }

    private static String copyToTempFileAndCalculateMD5(InputStream input, File tempFile) throws Exception {
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        try (FileOutputStream output = new FileOutputStream(tempFile)) {
            while ((read = input.read(buffer)) > 0) {
                md5Digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                total += read;
            }
            output.flush();
        } finally {
            input.close();
        }
        byte[] md5Bytes = md5Digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : md5Bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static int[] decodeImageBounds(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (FileInputStream fis = new FileInputStream(file)) {
            BitmapFactory.decodeStream(fis, null, options);
        } catch (Exception ignored) {}
        return new int[]{options.outWidth > 0 ? options.outWidth : 800, options.outHeight > 0 ? options.outHeight : 800};
    }
}
