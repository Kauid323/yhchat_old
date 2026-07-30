package com.nago8.chat.old;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.utils.LocaleHelper;
import com.ortiz.touchview.TouchImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

public class ImagePreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "image_url";
    private static final int REQUEST_SAVE_PERMISSION = 2001;

    private TouchImageView zoomableImage;
    private ProgressBar progressBar;
    private String imageUrl;
    private boolean imageLoaded = false;
    private int imageWidth = 0;
    private int imageHeight = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);

        zoomableImage = findViewById(R.id.zoomableImage);
        progressBar = findViewById(R.id.progressBar);
        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        AppCompatImageButton btnSave = findViewById(R.id.btnSave);

        btnBack.setOnClickListener(v -> onBackPressed());
        btnSave.setOnClickListener(this::showPopupMenu);

        loadImage();
    }

    private void showPopupMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        Menu menu = popup.getMenu();

        menu.add(0, 1, 0, "保存");
        menu.add(0, 2, 1, "分享");
        menu.add(0, 3, 2, "详情");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) { // 保存
                attemptSaveImage();
                return true;
            } else if (id == 2) { // 分享
                shareImage();
                return true;
            } else if (id == 3) { // 详情
                showImageDetails();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void loadImage() {
        if (imageUrl == null || imageUrl.length() == 0) {
            Toast.makeText(this, R.string.image_preview_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        GlideUrl glideUrl;
        if (imageUrl.contains(".jwznb.com")) {
            glideUrl = new GlideUrl(imageUrl, new LazyHeaders.Builder()
                    .addHeader("Referer", "http://myapp.jwznb.com")
                    .build());
        } else {
            glideUrl = new GlideUrl(imageUrl);
        }

        Glide.with(this)
                .asDrawable()
                .load(glideUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        progressBar.setVisibility(View.GONE);
                        imageLoaded = true;

                        imageWidth = resource.getIntrinsicWidth();
                        imageHeight = resource.getIntrinsicHeight();

                        // GIF 需要手动启动动画播放
                        if (resource instanceof GifDrawable) {
                            GifDrawable gif = (GifDrawable) resource;
                            gif.setLoopCount(GifDrawable.LOOP_INTRINSIC);
                            gif.start();
                        }
                        zoomableImage.setImageDrawable(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        stopGifIfRunning();
                    }
                });
    }

    private void stopGifIfRunning() {
        Drawable d = zoomableImage.getDrawable();
        if (d instanceof GifDrawable) {
            ((GifDrawable) d).stop();
        }
    }

    @Override
    protected void onDestroy() {
        stopGifIfRunning();
        super.onDestroy();
    }

    // ==================== 保存功能 ====================

    private void attemptSaveImage() {
        if (!imageLoaded) {
            Toast.makeText(this, R.string.image_preview_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performSaveInBackground(true);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_SAVE_PERMISSION);
            } else {
                performSaveInBackground(false);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SAVE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performSaveInBackground(false);
            } else {
                Toast.makeText(this, R.string.image_preview_save_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performSaveInBackground(boolean isScopedStorage) {
        Toast.makeText(this, "正在保存图片...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = isScopedStorage ? saveImageScopedStorage() : saveImageLegacy();
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(ImagePreviewActivity.this, "已保存至相册", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ImagePreviewActivity.this, R.string.image_preview_save_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private boolean saveImageScopedStorage() {
        try {
            String ext = getExtensionFromUrl(imageUrl);
            String mime = getMimeFromExtension(ext);
            String fileName = "yhchat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + "." + ext;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YunHuOld");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return false;

            OutputStream os = getContentResolver().openOutputStream(uri);
            boolean ok = downloadToStream(imageUrl, os);
            if (os != null) os.close();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ok) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                } else {
                    getContentResolver().delete(uri, null, null);
                    return false;
                }
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean saveImageLegacy() {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunHuOld");
            if (!dir.exists()) dir.mkdirs();

            String ext = getExtensionFromUrl(imageUrl);
            String mime = getMimeFromExtension(ext);
            String fileName = "yhchat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + "." + ext;
            File file = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            boolean ok = downloadToStream(imageUrl, fos);
            fos.close();

            if (ok) {
                android.media.MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{mime}, null);
                return true;
            } else {
                file.delete();
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 分享功能 ====================

    private void shareImage() {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        Toast.makeText(this, "正在准备分享...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                File cacheDir = new File(getCacheDir(), "shared_images");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                String ext = getExtensionFromUrl(imageUrl);
                File file = new File(cacheDir, "share_" + System.currentTimeMillis() + "." + ext);

                FileOutputStream fos = new FileOutputStream(file);
                boolean ok = downloadToStream(imageUrl, fos);
                fos.close();

                if (ok) {
                    Uri contentUri = FileProvider.getUriForFile(
                            ImagePreviewActivity.this,
                            getPackageName() + ".fileprovider",
                            file
                    );

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(getMimeFromExtension(ext));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, imageUrl);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    runOnUiThread(() -> startActivity(Intent.createChooser(shareIntent, "分享图片")));
                } else {
                    runOnUiThread(() -> Toast.makeText(ImagePreviewActivity.this, "分享失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ImagePreviewActivity.this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ==================== 详情弹窗功能 ====================

    private void showImageDetails() {
        if (!imageLoaded) {
            Toast.makeText(this, R.string.image_preview_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        String ext = getExtensionFromUrl(imageUrl).toUpperCase(Locale.getDefault());
        String dimensionStr = (imageWidth > 0 && imageHeight > 0) ? (imageWidth + " × " + imageHeight + " 像素") : "未知";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("图片详情");

        StringBuilder sb = new StringBuilder();
        sb.append("图片链接：\n").append(imageUrl).append("\n\n");
        sb.append("图片格式：").append(ext).append("\n");
        sb.append("分辨率尺寸：").append(dimensionStr).append("\n");
        sb.append("文件大小：正在计算...");

        builder.setMessage(sb.toString());
        builder.setPositiveButton("确定", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        // 异步计算图片文件大小
        new Thread(() -> {
            long sizeInBytes = getRemoteFileSize(imageUrl);
            String sizeStr = (sizeInBytes > 0) ? Formatter.formatFileSize(ImagePreviewActivity.this, sizeInBytes) : "未知";

            runOnUiThread(() -> {
                if (dialog.isShowing()) {
                    StringBuilder updatedSb = new StringBuilder();
                    updatedSb.append("图片链接：\n").append(imageUrl).append("\n\n");
                    updatedSb.append("图片格式：").append(ext).append("\n");
                    updatedSb.append("分辨率尺寸：").append(dimensionStr).append("\n");
                    updatedSb.append("文件大小：").append(sizeStr);
                    dialog.setMessage(updatedSb.toString());
                }
            });
        }).start();
    }

    private long getRemoteFileSize(String url) {
        try {
            Request.Builder reqBuilder = new Request.Builder().url(url).head();
            if (url.contains(".jwznb.com")) {
                reqBuilder.header("Referer", "http://myapp.jwznb.com");
            }
            Response response = ApiClient.getClient().newCall(reqBuilder.build()).execute();
            if (response.isSuccessful()) {
                String lenStr = response.header("Content-Length");
                response.close();
                if (lenStr != null && lenStr.length() > 0) {
                    return Long.parseLong(lenStr);
                }
            } else {
                response.close();
            }
        } catch (Exception ignored) {}

        // Fallback
        try {
            Request.Builder reqBuilder = new Request.Builder().url(url);
            if (url.contains(".jwznb.com")) {
                reqBuilder.header("Referer", "http://myapp.jwznb.com");
            }
            Response response = ApiClient.getClient().newCall(reqBuilder.build()).execute();
            if (response.isSuccessful() && response.body() != null) {
                long contentLength = response.body().contentLength();
                response.close();
                return contentLength;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    // ==================== 工具方法 ====================

    private boolean downloadToStream(String url, OutputStream os) {
        try {
            Request.Builder reqBuilder = new Request.Builder().url(url);
            if (url.contains(".jwznb.com")) {
                reqBuilder.header("Referer", "http://myapp.jwznb.com");
            }
            Response response = ApiClient.getClient().newCall(reqBuilder.build()).execute();
            if (!response.isSuccessful() || response.body() == null) return false;
            InputStream is = response.body().byteStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            is.close();
            response.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getExtensionFromUrl(String url) {
        if (url == null) return "jpg";
        String lower = url.toLowerCase(Locale.getDefault());
        if (lower.contains(".gif")) return "gif";
        if (lower.contains(".webp")) return "webp";
        if (lower.contains(".avif")) return "avif";
        if (lower.contains(".png")) return "png";
        if (lower.contains(".bmp")) return "bmp";
        return "jpg";
    }

    private static String getMimeFromExtension(String ext) {
        if ("gif".equals(ext)) return "image/gif";
        if ("webp".equals(ext)) return "image/webp";
        if ("avif".equals(ext)) return "image/avif";
        if ("png".equals(ext)) return "image/png";
        if ("bmp".equals(ext)) return "image/bmp";
        return "image/jpeg";
    }
}
