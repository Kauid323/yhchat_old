package com.nago8.chat.old;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.widget.ZoomableImageView;

import com.nago8.chat.old.net.ApiClient;

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

    private ZoomableImageView zoomableImage;
    private ProgressBar progressBar;
    private String imageUrl;
    private boolean imageLoaded = false;

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
        btnSave.setOnClickListener(v -> attemptSaveImage());

        loadImage();
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
                        zoomableImage.setImageDrawable(resource);
                        zoomableImage.resetZoom();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }

    private void attemptSaveImage() {
        if (!imageLoaded) {
            Toast.makeText(this, R.string.image_preview_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageScopedStorage();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_SAVE_PERMISSION);
            } else {
                saveImageLegacy();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SAVE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImageLegacy();
            } else {
                Toast.makeText(this, R.string.image_preview_save_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveImageScopedStorage() {
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
            if (uri == null) {
                Toast.makeText(this, R.string.image_preview_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }

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
                    Toast.makeText(this, R.string.image_preview_save_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (ok) {
                Toast.makeText(this, R.string.image_preview_saved, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.image_preview_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageLegacy() {
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
                Toast.makeText(this, R.string.image_preview_saved, Toast.LENGTH_SHORT).show();
            } else {
                file.delete();
                Toast.makeText(this, R.string.image_preview_save_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.image_preview_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

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
