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

public class FileUploadUtils {

    private static final String TAG = "FileUploadUtils";
    private static final String FILE_BUCKET = "chat68-file";
    private static final String FILE_DOMAIN = "https://chat-file.jwzhd.com/";

    public static class QiniuFileResult {
        public String key;
        public String hash;
        public long fsize;
        public String fileName;
        public String fileExtension;
        public String mimeType;
        public String fileUrl;
    }

    public interface TokenCallback {
        void onSuccess(String uploadToken);
        void onError(Exception e);
    }

    public interface UploadCallback {
        void onSuccess(QiniuFileResult result);
        void onError(Exception e);
    }

    /**
     * 获取七牛云文件上传 Token (/v1/misc/qiniu-token2)
     */
    public static void getQiniuFileUploadToken(String token, TokenCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return;
        }

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/misc/qiniu-token2")
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
                            callback.onError(new Exception("qiniu file token is empty"));
                        }
                    } else {
                        String msg = json.optString("msg", "获取文件上传token失败");
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
     * 上传单个文件到七牛云 (bucket: chat68-file)
     */
    public static void uploadFile(Context context, Uri fileUri, String uploadToken, UploadCallback callback) {
        new Thread(() -> {
            File tempFile = null;
            try {
                String originalFileName = getFileName(context, fileUri);
                if (originalFileName == null || originalFileName.isEmpty()) {
                    originalFileName = "file_" + System.currentTimeMillis();
                }

                String extension = getFileExtension(originalFileName);
                String mimeType = context.getContentResolver().getType(fileUri);
                if (mimeType == null) {
                    mimeType = getMimeTypeFromExtension(extension);
                }
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }

                tempFile = new File(context.getCacheDir(), "file_up_" + System.currentTimeMillis() + "_" + fileUri.hashCode() + "." + extension);

                InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    callback.onError(new Exception("无法读取文件输入流"));
                    return;
                }

                String md5 = copyToTempFileAndCalculateMD5(inputStream, tempFile);
                long fsize = tempFile.length();

                // 文件 key 规则: disk/MD5.扩展名
                String fileKey = "disk/" + md5 + "." + extension;

                String ak = uploadToken.split(":")[0];
                String queryUrl = "https://api.qiniu.com/v4/query?ak=" + ak + "&bucket=" + FILE_BUCKET;

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

                    QiniuFileResult res = new QiniuFileResult();
                    res.key = json.optString("key", fileKey);
                    res.hash = json.optString("hash", md5);
                    res.fsize = json.optLong("fsize", fsize);
                    res.fileName = originalFileName;
                    res.fileExtension = extension;
                    res.mimeType = mimeType;
                    res.fileUrl = FILE_DOMAIN + res.key;

                    callback.onSuccess(res);
                } else {
                    int errCode = uploadResp.code();
                    callback.onError(new Exception("文件上传失败 HTTP " + errCode));
                }
                if (uploadResp.body() != null) uploadResp.body().close();

            } catch (Exception e) {
                Log.e(TAG, "uploadFile error", e);
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

    private static String getFileExtension(String fileName) {
        if (fileName == null) return "dat";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "dat";
    }

    private static String getMimeTypeFromExtension(String extension) {
        if (extension == null) return null;
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
    }
}
