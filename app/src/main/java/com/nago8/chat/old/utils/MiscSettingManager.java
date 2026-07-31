package com.nago8.chat.old.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class MiscSettingManager {

    private static final String TAG = "MiscSettingManager";
    private static final MiscSettingManager instance = new MiscSettingManager();

    private int fileSizeLimitNormal = 40;
    private int fileSizeLimitVip = 1024;
    private int imageSizeLimitNormal = 40;
    private int imageSizeLimitVip = 50;
    private int videoSizeLimitNormal = 40;
    private int videoSizeLimitVip = 200;

    private boolean isLoaded = false;

    private MiscSettingManager() {}

    public static MiscSettingManager getInstance() {
        return instance;
    }

    public enum MediaType {
        IMAGE,
        VIDEO,
        FILE
    }

    public static class SizeCheckResult {
        public boolean isAllowed;
        public long fileSizeBytes;
        public double fileSizeMb;
        public int limitMb;
        public String errorMessage;
    }

    public void fetchSettings(Context context) {
        String token = PrefUtils.getToken(context);
        Request.Builder builder = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/misc/setting")
                .get();

        if (token != null && !token.isEmpty()) {
            builder.header("token", token);
        }

        ApiClient.getClient().newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "fetchSettings failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonStr = response.body().string();
                        JSONObject root = new JSONObject(jsonStr);
                        if (root.optInt("code", 0) == 1) {
                            JSONObject data = root.optJSONObject("data");
                            if (data != null) {
                                fileSizeLimitNormal = data.optInt("fileSizeLimitNormal", fileSizeLimitNormal);
                                fileSizeLimitVip = data.has("fileeSizeLimitVip")
                                        ? data.optInt("fileeSizeLimitVip", fileSizeLimitVip)
                                        : data.optInt("fileSizeLimitVip", fileSizeLimitVip);
                                imageSizeLimitNormal = data.optInt("imageSizeLimitNormal", imageSizeLimitNormal);
                                imageSizeLimitVip = data.optInt("imageSizeLimitVip", imageSizeLimitVip);
                                videoSizeLimitNormal = data.optInt("videoSizeLimitNormal", videoSizeLimitNormal);
                                videoSizeLimitVip = data.optInt("videoSizeLimitVip", videoSizeLimitVip);
                                isLoaded = true;
                                Log.d(TAG, "Settings loaded: imageNormal=" + imageSizeLimitNormal + "MB, fileNormal=" + fileSizeLimitNormal + "MB");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "parse settings error", e);
                    } finally {
                        response.body().close();
                    }
                }
            }
        });
    }

    public int getLimitMb(MediaType type, boolean isVip) {
        switch (type) {
            case IMAGE:
                return isVip ? imageSizeLimitVip : imageSizeLimitNormal;
            case VIDEO:
                return isVip ? videoSizeLimitVip : videoSizeLimitNormal;
            case FILE:
            default:
                return isVip ? fileSizeLimitVip : fileSizeLimitNormal;
        }
    }

    public SizeCheckResult checkMediaSize(Context context, Uri uri, MediaType type) {
        boolean isVip = PrefUtils.isVip(context);
        int limitMb = getLimitMb(type, isVip);
        long fileSizeBytes = getFileSizeBytes(context, uri);
        double fileSizeMb = (double) fileSizeBytes / (1024.0 * 1024.0);

        SizeCheckResult result = new SizeCheckResult();
        result.fileSizeBytes = fileSizeBytes;
        result.fileSizeMb = fileSizeMb;
        result.limitMb = limitMb;

        if (limitMb > 0 && fileSizeBytes > 0 && fileSizeMb > limitMb) {
            result.isAllowed = false;
            String typeName = type == MediaType.IMAGE ? "图片" : (type == MediaType.VIDEO ? "视频" : "文件");
            result.errorMessage = String.format(java.util.Locale.getDefault(), "%s大小超过云端限制 (最大%dMB，当前%.2fMB)", typeName, limitMb, fileSizeMb);
        } else {
            result.isAllowed = true;
            result.errorMessage = null;
        }
        return result;
    }

    public static long getFileSizeBytes(Context context, Uri uri) {
        if (uri == null || context == null) return 0;
        long size = 0;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "failed to get file size from ContentResolver", e);
            }
        }
        if (size <= 0 && uri.getPath() != null) {
            try {
                File file = new File(uri.getPath());
                if (file.exists()) {
                    size = file.length();
                }
            } catch (Exception ignored) {}
        }
        return size;
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
