package com.nago8.chat.old;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.list_message_by_mid_seq;
import com.nago8.chat.old.proto.list_message_by_mid_seq_send;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.utils.LocaleHelper;
import com.ortiz.touchview.TouchImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.Request;
import okhttp3.Response;

public class ImagePreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "image_url";
    /** 多图模式：ArrayList<String> 图片URL列表 */
    public static final String EXTRA_IMAGE_URLS = "image_urls";
    /** 多图模式：起始索引 */
    public static final String EXTRA_START_INDEX = "start_index";
    /** 多图模式：ArrayList<Long> 每张图对应的 msg_seq（用于 API 加载更多） */
    public static final String EXTRA_MSG_SEQS = "msg_seqs";
    public static final String EXTRA_CHAT_ID = "chat_id";
    public static final String EXTRA_CHAT_TYPE = "chat_type";
    public static final String EXTRA_TOKEN = "token";

    private static final int REQUEST_SAVE_PERMISSION = 2001;
    private static final int LOAD_MORE_COUNT = 10; // 每次加载更多图片数

    // ──── UI ────
    private ViewPager2 viewPager;
    private TextView tvPageCount;
    private ProgressBar progressLoadMore;

    // ──── 数据 ────
    private final List<String> imageUrls = new ArrayList<>();
    private final List<Long> msgSeqs = new ArrayList<>();
    private final Set<String> urlSet = new HashSet<>(); // 去重
    private ImagePagerAdapter adapter;

    private String chatId;
    private int chatType;
    private String token;

    // 加载更多防抖
    private volatile boolean isLoadingOlder = false;
    private volatile boolean isLoadingNewer = false;
    private volatile boolean noMoreOlder = false;
    private volatile boolean noMoreNewer = false;

    // 当前索引（保存菜单用）
    private int currentIndex = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        viewPager = findViewById(R.id.viewPager);
        tvPageCount = findViewById(R.id.tvPageCount);
        progressLoadMore = findViewById(R.id.progressLoadMore);
        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        AppCompatImageButton btnSave = findViewById(R.id.btnSave);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        btnBack.setOnClickListener(v -> onBackPressed());
        btnSave.setOnClickListener(this::showPopupMenu);

        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        chatType = getIntent().getIntExtra(EXTRA_CHAT_TYPE, 1);
        token = getIntent().getStringExtra(EXTRA_TOKEN);

        // 读取图片列表
        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_IMAGE_URLS);
        ArrayList<Long> seqs = (ArrayList<Long>) getIntent().getSerializableExtra(EXTRA_MSG_SEQS);
        int startIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

        if (urls != null && !urls.isEmpty()) {
            // 多图模式
            for (int i = 0; i < urls.size(); i++) {
                String u = urls.get(i);
                if (!urlSet.contains(u)) {
                    urlSet.add(u);
                    imageUrls.add(u);
                    msgSeqs.add(seqs != null && i < seqs.size() ? seqs.get(i) : 0L);
                }
            }
        } else {
            // 单图降级模式（兼容旧代码）
            String singleUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
            if (!TextUtils.isEmpty(singleUrl)) {
                imageUrls.add(singleUrl);
                msgSeqs.add(0L);
                urlSet.add(singleUrl);
            }
        }

        if (imageUrls.isEmpty()) {
            Toast.makeText(this, R.string.image_preview_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        adapter = new ImagePagerAdapter();
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);

        // 跳到起始图
        int safeIndex = Math.max(0, Math.min(startIndex, imageUrls.size() - 1));
        viewPager.setCurrentItem(safeIndex, false);
        currentIndex = safeIndex;
        updatePageCount(safeIndex);

        // 滑动监听
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentIndex = position;
                updatePageCount(position);

                // 划到最后一张（最新）→ 加载更新的图片
                if (position >= imageUrls.size() - 1) {
                    loadMoreImages(false);
                }
                // 划到第一张（最旧）→ 加载更早的图片
                if (position == 0) {
                    loadMoreImages(true);
                }
            }
        });

        // 多图时显示计数器
        if (imageUrls.size() > 1) {
            tvPageCount.setVisibility(View.VISIBLE);
        }
    }

    private void updatePageCount(int position) {
        if (imageUrls.size() > 1) {
            tvPageCount.setText((position + 1) + " / " + imageUrls.size());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  加载更多图片（API: v1/msg/pic-list-message-by-mid-seq）
    // ──────────────────────────────────────────────────────────────────────

    private void loadMoreImages(boolean older) {
        if (chatId == null || chatId.isEmpty()) return;
        if (token == null || token.isEmpty()) return;
        if (older && (isLoadingOlder || noMoreOlder)) return;
        if (!older && (isLoadingNewer || noMoreNewer)) return;

        // 取参照 msg_seq（边界消息的 seq）
        long refSeq;
        if (older) {
            // 最早那张
            refSeq = msgSeqs.isEmpty() ? 0L : msgSeqs.get(0);
        } else {
            // 最新那张
            refSeq = msgSeqs.isEmpty() ? 0L : msgSeqs.get(msgSeqs.size() - 1);
        }
        if (refSeq == 0L) return;

        if (older) isLoadingOlder = true;
        else isLoadingNewer = true;

        runOnUiThread(() -> progressLoadMore.setVisibility(View.VISIBLE));

        long earlierCount = older ? LOAD_MORE_COUNT : 0;
        long latestCount = older ? 0 : LOAD_MORE_COUNT;

        // 用 list_message_by_mid_seq_send：tag3=msg_seq(image_id), tag4=chat_type, tag5=chat_id,
        // tag6=earlier_quantities, tag7=latest_quantities（复用 unknown/msg_count 字段）
        list_message_by_mid_seq_send req = new list_message_by_mid_seq_send.Builder()
                .msg_seq(refSeq)
                .chat_type((long) chatType)
                .chat_id(chatId)
                .unknown(earlierCount)
                .msg_count(latestCount)
                .msg_id("")
                .build();

        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                okhttp3.MediaType.parse("application/x-protobuf"),
                req.encode()
        );
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/msg/pic-list-message-by-mid-seq")
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                if (older) isLoadingOlder = false;
                else isLoadingNewer = false;
                runOnUiThread(() -> progressLoadMore.setVisibility(View.GONE));
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull Response response) {
                if (older) isLoadingOlder = false;
                else isLoadingNewer = false;

                if (!response.isSuccessful() || response.body() == null) {
                    response.close();
                    runOnUiThread(() -> progressLoadMore.setVisibility(View.GONE));
                    return;
                }

                List<String> newUrls = new ArrayList<>();
                List<Long> newSeqs = new ArrayList<>();

                try {
                    list_message_by_mid_seq result = list_message_by_mid_seq.ADAPTER.decode(response.body().source());
                    if (result != null && result.msg != null) {
                        for (Msg msg : result.msg) {
                            if (msg == null || msg.content == null) continue;
                            // 图片消息 or 表情消息（sticker_url / image_url）
                            String url = null;
                            if (!TextUtils.isEmpty(msg.content.image_url)) {
                                url = msg.content.image_url;
                            } else if (!TextUtils.isEmpty(msg.content.sticker_url)) {
                                url = msg.content.sticker_url;
                            }
                            if (url != null && !urlSet.contains(url)) {
                                newUrls.add(url);
                                newSeqs.add(msg.msg_seq);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("ImagePreview", "decode error", e);
                } finally {
                    response.close();
                }

                final List<String> finalUrls = newUrls;
                final List<Long> finalSeqs = newSeqs;

                runOnUiThread(() -> {
                    progressLoadMore.setVisibility(View.GONE);
                    if (finalUrls.isEmpty()) {
                        if (older) noMoreOlder = true;
                        else noMoreNewer = true;
                        return;
                    }

                    int savedPosition = viewPager.getCurrentItem();

                    if (older) {
                        // 往前插入（更早的图在前面）
                        // 保持从旧到新的顺序（API 返回的按时间排序，旧在前）
                        for (int i = 0; i < finalUrls.size(); i++) {
                            String u = finalUrls.get(i);
                            urlSet.add(u);
                            imageUrls.add(0, u);
                            msgSeqs.add(0, finalSeqs.get(i));
                        }
                        adapter.notifyItemRangeInserted(0, finalUrls.size());
                        // 维持当前浏览位置不跳动
                        viewPager.setCurrentItem(savedPosition + finalUrls.size(), false);
                    } else {
                        // 追加到末尾（更新的图在后面）
                        int insertPos = imageUrls.size();
                        for (int i = 0; i < finalUrls.size(); i++) {
                            String u = finalUrls.get(i);
                            urlSet.add(u);
                            imageUrls.add(u);
                            msgSeqs.add(finalSeqs.get(i));
                        }
                        adapter.notifyItemRangeInserted(insertPos, finalUrls.size());
                    }

                    updatePageCount(viewPager.getCurrentItem());
                    if (imageUrls.size() > 1) {
                        tvPageCount.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    //  ViewPager2 Adapter
    // ──────────────────────────────────────────────────────────────────────

    private class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.PageHolder> {

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_page, parent, false);
            return new PageHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            holder.bind(imageUrls.get(position));
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            TouchImageView zoomableImage;
            ProgressBar itemProgress;

            PageHolder(@NonNull View itemView) {
                super(itemView);
                zoomableImage = itemView.findViewById(R.id.zoomableImage);
                itemProgress = itemView.findViewById(R.id.itemProgress);
            }

            void bind(String url) {
                if (TextUtils.isEmpty(url)) return;
                itemProgress.setVisibility(View.VISIBLE);
                zoomableImage.setImageDrawable(null);

                // 在后台线程下载图片到临时文件再交给 Glide（与原来逻辑一致）
                final String targetUrl = normalizeUrl(url);
                new Thread(() -> {
                    try {
                        Request.Builder reqBuilder = new Request.Builder().url(targetUrl);
                        if (targetUrl.contains(".jwznb.com")) {
                            reqBuilder.addHeader("Referer", "http://myapp.jwznb.com");
                        }
                        Response response = ApiClient.getClient().newCall(reqBuilder.build()).execute();
                        if (!response.isSuccessful() || response.body() == null) {
                            runOnUiThread(() -> itemProgress.setVisibility(View.GONE));
                            return;
                        }
                        File tempFile = new File(getCacheDir(), "preview_" + System.nanoTime() + ".tmp");
                        InputStream is = response.body().byteStream();
                        FileOutputStream fos = new FileOutputStream(tempFile);
                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = is.read(buf)) != -1) fos.write(buf, 0, read);
                        fos.flush();
                        fos.close();
                        is.close();

                        runOnUiThread(() -> {
                            if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                            itemProgress.setVisibility(View.GONE);

                            boolean isGif = "gif".equalsIgnoreCase(getExtensionFromUrl(targetUrl));
                            if (isGif) {
                                Glide.with(getApplicationContext())
                                        .load(tempFile)
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .skipMemoryCache(true)
                                        .into(new CustomTarget<Drawable>() {
                                            @Override
                                            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                                                if (resource instanceof GifDrawable) {
                                                    GifDrawable gif = (GifDrawable) resource;
                                                    gif.setLoopCount(GifDrawable.LOOP_INTRINSIC);
                                                    gif.start();
                                                }
                                                zoomableImage.setImageDrawable(resource);
                                            }

                                            @Override
                                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                                                loadSampledBitmapFallback(tempFile);
                                            }

                                            @Override
                                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                                stopGifIfRunning(zoomableImage);
                                            }
                                        });
                            } else {
                                loadSampledBitmapFallback(tempFile);
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> itemProgress.setVisibility(View.GONE));
                    }
                }).start();
            }

            private void loadSampledBitmapFallback(File file) {
                try {
                    Bitmap bmp = decodeSampledBitmapFromFile(file.getAbsolutePath(), 2048, 2048);
                    if (bmp != null) {
                        zoomableImage.setImageBitmap(bmp);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (Build.VERSION.SDK_INT < 21 || trimmed.contains(".jwznb.com")) {
            if (trimmed.startsWith("https://")) {
                return "http://" + trimmed.substring(8);
            }
        }
        return trimmed;
    }

    private void stopGifIfRunning(TouchImageView iv) {
        Drawable d = iv.getDrawable();
        if (d instanceof GifDrawable) ((GifDrawable) d).stop();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  弹出菜单（保存 / 分享 / 详情）
    // ──────────────────────────────────────────────────────────────────────

    private void showPopupMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        Menu menu = popup.getMenu();
        menu.add(0, 1, 0, "保存");
        menu.add(0, 2, 1, "分享");
        menu.add(0, 3, 2, "详情");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) { attemptSaveImage(); return true; }
            if (id == 2) { shareImage(); return true; }
            if (id == 3) { showImageDetails(); return true; }
            return false;
        });
        popup.show();
    }

    private String currentImageUrl() {
        if (imageUrls.isEmpty()) return "";
        int idx = Math.max(0, Math.min(currentIndex, imageUrls.size() - 1));
        return imageUrls.get(idx);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  保存
    // ──────────────────────────────────────────────────────────────────────

    private void attemptSaveImage() {
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
        final String url = currentImageUrl();
        new Thread(() -> {
            boolean success = isScopedStorage ? saveImageScopedStorage(url) : saveImageLegacy(url);
            runOnUiThread(() -> {
                if (success) Toast.makeText(this, "已保存至相册", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, R.string.image_preview_save_failed, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private boolean saveImageScopedStorage(String url) {
        try {
            String ext = getExtensionFromUrl(url);
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
            boolean ok = downloadToStream(url, os);
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
        } catch (Exception e) { return false; }
    }

    private boolean saveImageLegacy(String url) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunHuOld");
            if (!dir.exists()) dir.mkdirs();
            String ext = getExtensionFromUrl(url);
            String fileName = "yhchat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + "." + ext;
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            boolean ok = downloadToStream(url, fos);
            fos.close();
            if (ok) {
                android.media.MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{getMimeFromExtension(ext)}, null);
            } else {
                file.delete();
            }
            return ok;
        } catch (Exception e) { return false; }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  分享
    // ──────────────────────────────────────────────────────────────────────

    private void shareImage() {
        final String url = currentImageUrl();
        if (url.isEmpty()) return;
        Toast.makeText(this, "正在准备分享...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File cacheDir = new File(getCacheDir(), "shared_images");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                String ext = getExtensionFromUrl(url);
                File file = new File(cacheDir, "share_" + System.currentTimeMillis() + "." + ext);
                FileOutputStream fos = new FileOutputStream(file);
                boolean ok = downloadToStream(url, fos);
                fos.close();
                if (ok) {
                    Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(getMimeFromExtension(ext));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, url);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    runOnUiThread(() -> startActivity(Intent.createChooser(shareIntent, "分享图片")));
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  详情
    // ──────────────────────────────────────────────────────────────────────

    private void showImageDetails() {
        final String url = currentImageUrl();
        String ext = getExtensionFromUrl(url).toUpperCase(Locale.getDefault());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("图片详情");
        StringBuilder sb = new StringBuilder();
        sb.append("图片链接：\n").append(url).append("\n\n");
        sb.append("图片格式：").append(ext).append("\n");
        sb.append("文件大小：正在计算...");
        builder.setMessage(sb.toString());
        builder.setPositiveButton("确定", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        new Thread(() -> {
            long size = getRemoteFileSize(url);
            String sizeStr = size > 0 ? Formatter.formatFileSize(this, size) : "未知";
            runOnUiThread(() -> {
                if (dialog.isShowing()) {
                    StringBuilder sb2 = new StringBuilder();
                    sb2.append("图片链接：\n").append(url).append("\n\n");
                    sb2.append("图片格式：").append(ext).append("\n");
                    sb2.append("文件大小：").append(sizeStr);
                    dialog.setMessage(sb2.toString());
                }
            });
        }).start();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  工具方法
    // ──────────────────────────────────────────────────────────────────────

    private long getRemoteFileSize(String url) {
        try {
            Request.Builder b = new Request.Builder().url(url).head();
            if (url.contains(".jwznb.com")) b.header("Referer", "http://myapp.jwznb.com");
            Response r = ApiClient.getClient().newCall(b.build()).execute();
            if (r.isSuccessful()) {
                String len = r.header("Content-Length");
                r.close();
                if (len != null) return Long.parseLong(len);
            } else r.close();
        } catch (Exception ignored) {}
        return -1;
    }

    private boolean downloadToStream(String url, OutputStream os) {
        try {
            Request.Builder b = new Request.Builder().url(url);
            if (url.contains(".jwznb.com")) b.header("Referer", "http://myapp.jwznb.com");
            Response r = ApiClient.getClient().newCall(b.build()).execute();
            if (!r.isSuccessful() || r.body() == null) return false;
            InputStream is = r.body().byteStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
            is.close();
            r.close();
            return true;
        } catch (Exception e) { return false; }
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

    public static Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
            if (bitmap == null) return null;

            // 彻底防止硬件 Canvas 报错 Canvas: trying to draw too large bitmap (限制单边 max 4096px 且总内存 < 50MB)
            int maxDim = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (maxDim > 4096) {
                float scale = 4096f / maxDim;
                int newW = Math.round(bitmap.getWidth() * scale);
                int newH = Math.round(bitmap.getHeight() * scale);
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }
            return bitmap;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = Math.max(heightRatio, widthRatio);
        }
        return Math.max(1, inSampleSize);
    }
}
