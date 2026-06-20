package com.nago8.chat.old.net;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

public class FileDownloadManager {

    public interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(File file);
        void onError(Exception error);
        void onCancel();
    }

    private static FileDownloadManager instance;
    private final Map<String, Call> activeCalls = new ConcurrentHashMap<>();
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();

    private FileDownloadManager() {
    }

    public static synchronized FileDownloadManager getInstance() {
        if (instance == null) {
            instance = new FileDownloadManager();
        }
        return instance;
    }

    public void download(final Context context, final String url, final String fileName, final DownloadCallback callback) {
        if (isDownloading(url)) {
            return;
        }

        Request.Builder reqBuilder = new Request.Builder().url(url);
        // 云湖所有资源均需要 Referer，统一添加
        reqBuilder.header("Referer", "http://myapp.jwznb.com");

        Call call = ApiClient.getClient().newCall(reqBuilder.build());
        activeCalls.put(url, call);
        progressMap.put(url, 0);

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                activeCalls.remove(url);
                progressMap.remove(url);
                if (call.isCanceled()) {
                    callback.onCancel();
                } else {
                    callback.onError(e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    activeCalls.remove(url);
                    progressMap.remove(url);
                    callback.onError(new java.io.IOException("HTTP " + response.code()));
                    return;
                }

                File outputFile = getOutputFile(context, fileName);
                if (outputFile == null) {
                    activeCalls.remove(url);
                    progressMap.remove(url);
                    callback.onError(new java.io.IOException("cannot create output file"));
                    return;
                }

                long totalBytes = response.body().contentLength();
                InputStream is = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(outputFile);
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int lastPercent = -1;

                try {
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        if (call.isCanceled()) {
                            fos.close();
                            is.close();
                            outputFile.delete();
                            activeCalls.remove(url);
                            progressMap.remove(url);
                            callback.onCancel();
                            return;
                        }
                        fos.write(buffer, 0, len);
                        downloaded += len;
                        if (totalBytes > 0) {
                            int percent = (int) (downloaded * 100 / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                progressMap.put(url, percent);
                                callback.onProgress(percent);
                            }
                        }
                    }
                    fos.close();
                    is.close();
                    activeCalls.remove(url);
                    progressMap.remove(url);
                    callback.onComplete(outputFile);
                } catch (Exception e) {
                    try { fos.close(); } catch (Exception ignored) {}
                    try { is.close(); } catch (Exception ignored) {}
                    outputFile.delete();
                    activeCalls.remove(url);
                    progressMap.remove(url);
                    if (call.isCanceled()) {
                        callback.onCancel();
                    } else {
                        callback.onError(e);
                    }
                }
            }
        });
    }

    public void cancel(String url) {
        Call call = activeCalls.get(url);
        if (call != null) {
            call.cancel();
        }
    }

    public boolean isDownloading(String url) {
        return activeCalls.containsKey(url);
    }

    public int getProgress(String url) {
        Integer p = progressMap.get(url);
        return p != null ? p : -1;
    }

    private File getOutputFile(Context context, String fileName) {
        File dir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            dir = new File(context.getExternalFilesDir(null), "Downloads");
        } else {
            dir = new File(Environment.getExternalStorageDirectory(), "Download/YunHuOld");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(context.getFilesDir(), "Downloads");
            if (!dir.exists()) dir.mkdirs();
        }
        String safeName = fileName != null && fileName.length() > 0 ? fileName : "download_file";
        return new File(dir, safeName);
    }

    public static void openFile(Context context, File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = getMimeType(file.getName());
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 必须用 FileProvider，不能直接 file://
                uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // Android 4-6 可以直接用 file://
                uri = Uri.fromFile(file);
            }
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // 没有能打开此文件的应用
            Toast.makeText(context, "无法打开此文件", Toast.LENGTH_SHORT).show();
        }
    }

    private static String getMimeType(String fileName) {
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) ext = fileName.substring(dot + 1).toLowerCase();
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "*/*";
    }
}
