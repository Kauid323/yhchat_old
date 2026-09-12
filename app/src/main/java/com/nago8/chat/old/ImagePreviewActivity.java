package com.nago8.chat.old;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Animatable;
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
import android.view.TextureView;
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
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.nago8.chat.old.cache.StickerCache;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.list_message_by_mid_seq;
import com.nago8.chat.old.proto.list_message_by_mid_seq_send;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.utils.LivePhotoUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.ortiz.touchview.TouchImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.Request;
import okhttp3.Response;

public class ImagePreviewActivity extends AppCompatActivity {

    private static final String TAG = "ImagePreview";

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
    @SuppressWarnings("unchecked")
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
        ArrayList<Long> seqs = null;
        Serializable serializableSeqs = getIntent().getSerializableExtra(EXTRA_MSG_SEQS);
        if (serializableSeqs instanceof ArrayList) {
            seqs = (ArrayList<Long>) serializableSeqs;
        }
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

        Log.i(TAG, "[ImagePreview] onCreate() loaded " + imageUrls.size() + " URLs, startIndex=" + startIndex + ", urls=" + imageUrls);
        if (imageUrls.isEmpty()) {
            Log.w(TAG, "[ImagePreview] imageUrls is empty, finishing activity");
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
            tvPageCount.setText(String.format(Locale.getDefault(), "%d / %d", position + 1, imageUrls.size()));
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
        // tag6=earlier_quantities, tag7=latest_quantities
        list_message_by_mid_seq_send req = new list_message_by_mid_seq_send.Builder()
                .msg_seq(refSeq)
                .chat_type(chatType)
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
                    Log.e(TAG, "decode error", e);
                } finally {
                    response.close();
                }

                if (newUrls.isEmpty()) {
                    if (older) noMoreOlder = true;
                    else noMoreNewer = true;
                    runOnUiThread(() -> progressLoadMore.setVisibility(View.GONE));
                    return;
                }

                final List<String> finalUrls = newUrls;
                final List<Long> finalSeqs = newSeqs;

                runOnUiThread(() -> {
                    progressLoadMore.setVisibility(View.GONE);

                    if (older) {
                        // 插入到最前面
                        for (int i = finalUrls.size() - 1; i >= 0; i--) {
                            String u = finalUrls.get(i);
                            urlSet.add(u);
                            imageUrls.add(0, u);
                            msgSeqs.add(0, finalSeqs.get(i));
                        }
                        adapter.notifyItemRangeInserted(0, finalUrls.size());
                        int newCurrent = viewPager.getCurrentItem() + finalUrls.size();
                        viewPager.setCurrentItem(newCurrent, false);
                    } else {
                        // 插入到最后面
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
        public void onViewRecycled(@NonNull PageHolder holder) {
            super.onViewRecycled(holder);
            holder.releasePlayer();
            stopGifIfRunning(holder.zoomableImage);
            try {
                Glide.with(getApplicationContext()).clear(holder.zoomableImage);
            } catch (Exception ignored) {}
        }

        @Override
        public int getItemCount() {
            return imageUrls.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            TouchImageView zoomableImage;
            TextureView textureViewLive;
            View layoutLiveBadge;
            ProgressBar itemProgress;

            private File currentLiveVideoFile = null;
            private android.media.MediaPlayer mediaPlayer = null;
            private boolean isPlayingLive = false;

            @SuppressLint("ClickableViewAccessibility")
            PageHolder(@NonNull View itemView) {
                super(itemView);
                zoomableImage = itemView.findViewById(R.id.zoomableImage);
                textureViewLive = itemView.findViewById(R.id.textureViewLive);
                layoutLiveBadge = itemView.findViewById(R.id.layoutLiveBadge);
                itemProgress = itemView.findViewById(R.id.itemProgress);

                // 长按图片播放实况，松开手指停止实况
                zoomableImage.setOnLongClickListener(v -> {
                    if (currentLiveVideoFile != null && currentLiveVideoFile.exists()) {
                        startLivePlayback();
                        return true;
                    }
                    return false;
                });

                zoomableImage.setOnTouchListener((v, event) -> {
                    if (isPlayingLive) {
                        int action = event.getAction();
                        if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                            stopLivePlayback();
                            v.performClick();
                        }
                    }
                    return false;
                });

                // 点击实况徽章切换播放/暂停
                layoutLiveBadge.setOnClickListener(v -> {
                    if (isPlayingLive) {
                        stopLivePlayback();
                    } else {
                        startLivePlayback();
                    }
                });

                textureViewLive.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {
                        if (mediaPlayer != null) {
                            try {
                                mediaPlayer.setSurface(new android.view.Surface(surface));
                            } catch (Exception ignored) {}
                        }
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {}

                    @Override
                    public boolean onSurfaceTextureDestroyed(@NonNull android.graphics.SurfaceTexture surface) {
                        if (mediaPlayer != null) {
                            try {
                                mediaPlayer.setSurface(null);
                            } catch (Exception ignored) {}
                        }
                        return true;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(@NonNull android.graphics.SurfaceTexture surface) {}
                });
            }

            private void adjustTextureViewSize(int videoWidth, int videoHeight) {
                if (videoWidth <= 0 || videoHeight <= 0) return;
                int containerWidth = itemView.getWidth();
                int containerHeight = itemView.getHeight();
                if (containerWidth <= 0 || containerHeight <= 0) return;

                float containerRatio = (float) containerWidth / containerHeight;
                float videoRatio = (float) videoWidth / videoHeight;

                int targetW;
                int targetH;
                if (videoRatio > containerRatio) {
                    targetW = containerWidth;
                    targetH = (int) (containerWidth / videoRatio);
                } else {
                    targetH = containerHeight;
                    targetW = (int) (containerHeight * videoRatio);
                }

                ViewGroup.LayoutParams lp = textureViewLive.getLayoutParams();
                if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                    android.widget.FrameLayout.LayoutParams flp = (android.widget.FrameLayout.LayoutParams) lp;
                    flp.width = targetW;
                    flp.height = targetH;
                    flp.gravity = android.view.Gravity.CENTER;
                    textureViewLive.setLayoutParams(flp);
                }
            }

            private void startLivePlayback() {
                if (currentLiveVideoFile == null || !currentLiveVideoFile.exists()) return;
                isPlayingLive = true;
                textureViewLive.setVisibility(View.VISIBLE);

                try {
                    if (mediaPlayer == null) {
                        mediaPlayer = new android.media.MediaPlayer();
                        mediaPlayer.setLooping(true);
                        mediaPlayer.setDataSource(currentLiveVideoFile.getAbsolutePath());
                        if (textureViewLive.getSurfaceTexture() != null) {
                            mediaPlayer.setSurface(new android.view.Surface(textureViewLive.getSurfaceTexture()));
                        }
                        mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> adjustTextureViewSize(width, height));
                        mediaPlayer.setOnPreparedListener(mp -> {
                            adjustTextureViewSize(mp.getVideoWidth(), mp.getVideoHeight());
                            if (isPlayingLive) {
                                mp.start();
                            }
                        });
                        mediaPlayer.prepareAsync();
                    } else {
                        adjustTextureViewSize(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
                        mediaPlayer.start();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to play live photo video", e);
                }
            }

            private void stopLivePlayback() {
                isPlayingLive = false;
                textureViewLive.setVisibility(View.GONE);
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                            mediaPlayer.seekTo(0);
                        }
                    } catch (Exception ignored) {}
                }
            }

            private void releasePlayer() {
                stopLivePlayback();
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                    } catch (Exception ignored) {}
                    mediaPlayer = null;
                }
                if (currentLiveVideoFile != null && currentLiveVideoFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    currentLiveVideoFile.delete();
                    currentLiveVideoFile = null;
                }
            }

            void bind(String url) {
                releasePlayer();
                if (TextUtils.isEmpty(url)) {
                    Log.w(TAG, "[ImagePreview] bind() called with empty URL");
                    return;
                }
                itemProgress.setVisibility(View.VISIBLE);
                layoutLiveBadge.setVisibility(View.GONE);
                zoomableImage.setImageDrawable(null);

                final String targetUrl = normalizeUrl(url);
                final String cleanUrl = stripQiniuParams(targetUrl);
                Log.i(TAG, "[ImagePreview] === BIND START ===");
                Log.i(TAG, "[ImagePreview] Raw URL: " + url);
                Log.i(TAG, "[ImagePreview] Target URL: " + targetUrl);
                Log.i(TAG, "[ImagePreview] Clean URL: " + cleanUrl);

                new Thread(() -> {
                    File tempFile = null;
                    try {
                        // 1. 本地文件直接加载
                        if (targetUrl.startsWith("/") || targetUrl.startsWith("file://")) {
                            String path = targetUrl.startsWith("file://") ? targetUrl.substring(7) : targetUrl;
                            File localFile = new File(path);
                            Log.i(TAG, "[ImagePreview] Checking local path: " + path + ", exists=" + localFile.exists() + ", len=" + (localFile.exists() ? localFile.length() : 0));
                            if (localFile.exists() && localFile.length() > 0) {
                                renderFileOrFallback(localFile, cleanUrl);
                                return;
                            }
                        }

                        // 2. 对于大图/GIF预览，绝不使用 AvatarCache(其为120x120静态缩略图)，始终请求完整原图/完整GIF
                        // 如果是表情包，仅当命中 StickerCache 时复用
                        String lowerClean = cleanUrl.toLowerCase(Locale.getDefault());
                        boolean isGif = lowerClean.contains(".gif") || lowerClean.contains("/expression/") || lowerClean.contains(".tmp");
                        if (!isGif) {
                            File cachedSticker = StickerCache.getStickerFile(ImagePreviewActivity.this, cleanUrl);
                            if (cachedSticker != null && cachedSticker.exists() && cachedSticker.length() > 64) {
                                Log.i(TAG, "[ImagePreview] Found valid StickerCache file: " + cachedSticker.getAbsolutePath() + ", size=" + cachedSticker.length());
                                renderFileOrFallback(cachedSticker, cleanUrl);
                                return;
                            }
                        }

                        // 3. 网络下载到本地临时文件
                        Log.i(TAG, "[ImagePreview] Downloading via OkHttp from: " + cleanUrl);
                        Request.Builder reqBuilder = new Request.Builder().url(cleanUrl);
                        if (cleanUrl.contains(".jwznb.com")) {
                            reqBuilder.addHeader("Referer", "https://myapp.jwznb.com");
                            reqBuilder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
                        }
                        Response response = ApiClient.getClient().newCall(reqBuilder.build()).execute();
                        Log.i(TAG, "[ImagePreview] OkHttp response code=" + response.code() + ", contentType=" + response.header("Content-Type"));
                        
                        if ((!response.isSuccessful() || response.body() == null) && !cleanUrl.equals(targetUrl)) {
                            Log.w(TAG, "[ImagePreview] Clean URL failed with code " + response.code() + ", retrying targetUrl: " + targetUrl);
                            reqBuilder = new Request.Builder().url(targetUrl);
                            if (targetUrl.contains(".jwznb.com")) {
                                reqBuilder.addHeader("Referer", "https://myapp.jwznb.com");
                                reqBuilder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
                            }
                            response = ApiClient.getClient().newCall(reqBuilder.build()).execute();
                            Log.i(TAG, "[ImagePreview] Retried targetUrl response code=" + response.code());
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            byte[] bytes = response.body().bytes();
                            int byteLen = bytes != null ? bytes.length : 0;
                            Log.i(TAG, "[ImagePreview] OkHttp received bytes length=" + byteLen);
                            if (bytes != null && bytes.length > 64) {
                                String head = new String(bytes, 0, Math.min(bytes.length, 64)).toLowerCase(Locale.getDefault());
                                if (!head.contains("html") && !head.contains("xml") && !head.contains("error") && !head.contains("denied")) {
                                    String ext = getExtensionFromUrl(cleanUrl);
                                    tempFile = new File(getCacheDir(), "preview_" + System.nanoTime() + "." + ext);
                                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                                        fos.write(bytes);
                                        fos.flush();
                                    }
                                    Log.i(TAG, "[ImagePreview] Saved temp file: " + tempFile.getAbsolutePath() + ", ext=" + ext + ", size=" + tempFile.length());
                                    renderFileOrFallback(tempFile, cleanUrl);
                                    return;
                                } else {
                                    Log.e(TAG, "[ImagePreview] OkHttp response body is error/HTML content: " + head);
                                }
                            }
                        } else {
                            Log.e(TAG, "[ImagePreview] OkHttp download unsuccessful, code=" + response.code());
                        }

                        // 4. OkHttp 无法直接下载时，降级使用 Glide Url 自带网络加载
                        Log.i(TAG, "[ImagePreview] Falling back to direct Glide Url loading: " + cleanUrl);
                        runOnUiThread(() -> {
                            if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                            loadGlideDirectly(cleanUrl);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "[ImagePreview] Error during image loading pipeline", e);
                        runOnUiThread(() -> {
                            if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                            loadGlideDirectly(cleanUrl);
                        });
                    }
                }).start();
            }

            private void renderFileOrFallback(File file, String sourceUrl) {
                File extractedLiveVideo = LivePhotoUtils.extractLiveVideo(file, getCacheDir());
                runOnUiThread(() -> {
                    if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                    itemProgress.setVisibility(View.GONE);

                    if (extractedLiveVideo != null && extractedLiveVideo.exists()) {
                        currentLiveVideoFile = extractedLiveVideo;
                        layoutLiveBadge.setVisibility(View.VISIBLE);
                        Log.i(TAG, "[ImagePreview] Live photo detected: " + extractedLiveVideo.getAbsolutePath());
                    } else {
                        layoutLiveBadge.setVisibility(View.GONE);
                    }

                    Log.i(TAG, "[ImagePreview] Calling Glide.load(File): " + file.getAbsolutePath() + ", size=" + file.length());
                    Glide.with(ImagePreviewActivity.this)
                            .asDrawable()
                            .load(file)
                            .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .into(new CustomTarget<Drawable>() {
                                @Override
                                public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                    if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                                    itemProgress.setVisibility(View.GONE);
                                    boolean isAnim = resource instanceof Animatable;
                                    Log.i(TAG, "[ImagePreview] Glide.load(File) onResourceReady: class=" + resource.getClass().getName()
                                            + ", animatable=" + isAnim + ", size=[" + resource.getIntrinsicWidth() + "x" + resource.getIntrinsicHeight() + "]");
                                    zoomableImage.setImageDrawable(resource);
                                    if (isAnim) {
                                        ((Animatable) resource).start();
                                    }
                                    zoomableImage.post(() -> {
                                        try {
                                            zoomableImage.resetZoom();
                                            zoomableImage.invalidate();
                                        } catch (Exception ignored) {}
                                    });
                                }

                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder) {
                                    zoomableImage.setImageDrawable(placeholder);
                                }

                                @Override
                                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                    Log.e(TAG, "[ImagePreview] Glide.load(File) onLoadFailed for " + file.getAbsolutePath());
                                    if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                                    loadGlideDirectly(sourceUrl);
                                }
                            });
                });
            }

            private void loadGlideDirectly(String sourceUrl) {
                if (TextUtils.isEmpty(sourceUrl)) {
                    Log.w(TAG, "[ImagePreview] loadGlideDirectly called with empty sourceUrl");
                    itemProgress.setVisibility(View.GONE);
                    return;
                }
                Log.i(TAG, "[ImagePreview] loadGlideDirectly() starting for: " + sourceUrl);
                GlideUrl glideUrl;
                if (sourceUrl.contains(".jwznb.com")) {
                    glideUrl = new GlideUrl(sourceUrl, new LazyHeaders.Builder()
                            .addHeader("Referer", "https://myapp.jwznb.com")
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                            .build());
                } else {
                    glideUrl = new GlideUrl(sourceUrl);
                }

                Glide.with(ImagePreviewActivity.this)
                        .asDrawable()
                        .load(glideUrl)
                        .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                        .placeholder(R.drawable.ic_image)
                        .error(R.drawable.ic_image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(new CustomTarget<Drawable>() {
                            @Override
                            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                                itemProgress.setVisibility(View.GONE);
                                boolean isAnim = resource instanceof Animatable;
                                Log.i(TAG, "[ImagePreview] loadGlideDirectly() onResourceReady: class=" + resource.getClass().getName()
                                        + ", animatable=" + isAnim + ", size=[" + resource.getIntrinsicWidth() + "x" + resource.getIntrinsicHeight() + "]");
                                zoomableImage.setImageDrawable(resource);
                                if (isAnim) {
                                    ((Animatable) resource).start();
                                }
                                zoomableImage.post(() -> {
                                    try {
                                        zoomableImage.resetZoom();
                                        zoomableImage.invalidate();
                                    } catch (Exception ignored) {}
                                });
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                zoomableImage.setImageDrawable(placeholder);
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                itemProgress.setVisibility(View.GONE);
                                Log.e(TAG, "[ImagePreview] loadGlideDirectly() onLoadFailed for: " + sourceUrl);
                            }
                        });
            }
        }
    }

    private static String stripQiniuParams(String url) {
        if (url == null) return "";
        int qIdx = url.indexOf('?');
        if (qIdx != -1) {
            String lower = url.toLowerCase(Locale.getDefault());
            if (lower.contains("imageview2") || lower.contains("imagemogr2") || lower.contains(".tmp") || lower.contains(".gif")) {
                return url.substring(0, qIdx);
            }
        }
        return url;
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "";

        // 本地绝对路径或 file:// / content://
        if (trimmed.startsWith("/") || trimmed.startsWith("file://") || trimmed.startsWith("content://")) {
            return trimmed;
        }

        // 补全前缀
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            if (trimmed.startsWith("/")) {
                trimmed = "https://chat-img.jwznb.com" + trimmed;
            } else {
                trimmed = "https://chat-img.jwznb.com/" + trimmed;
            }
        }

        // Android 4.x SSL 兼容
        if (Build.VERSION.SDK_INT < 21 && trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed.substring(8);
        }
        return trimmed;
    }

    private void stopGifIfRunning(TouchImageView iv) {
        if (iv != null) {
            Drawable d = iv.getDrawable();
            if (d instanceof Animatable) {
                try {
                    ((Animatable) d).stop();
                } catch (Exception ignored) {}
            }
            iv.setImageDrawable(null);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  弹出菜单（保存 / 分享 / 详情）
    // ──────────────────────────────────────────────────────────────────────

    private void showPopupMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        Menu menu = popup.getMenu();
        menu.add(0, 1, 0, R.string.action_save);
        menu.add(0, 2, 1, R.string.action_share);
        menu.add(0, 3, 2, R.string.action_details);

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
        Toast.makeText(this, R.string.image_saving, Toast.LENGTH_SHORT).show();
        final String url = currentImageUrl();
        new Thread(() -> {
            boolean success = isScopedStorage ? saveImageScopedStorage(url) : saveImageLegacy(url);
            runOnUiThread(() -> {
                if (success) Toast.makeText(this, R.string.image_saved_to_album, Toast.LENGTH_SHORT).show();
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
            boolean ok;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                ok = downloadToStream(url, os);
            }
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

    private boolean saveImageLegacy(String url) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "YunHuOld");
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            String ext = getExtensionFromUrl(url);
            String fileName = "yhchat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + "." + ext;
            File file = new File(dir, fileName);
            boolean ok;
            try (FileOutputStream fos = new FileOutputStream(file)) {
                ok = downloadToStream(url, fos);
            }
            if (ok) {
                android.media.MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{getMimeFromExtension(ext)}, null);
            } else {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  分享
    // ──────────────────────────────────────────────────────────────────────

    private void shareImage() {
        final String url = currentImageUrl();
        if (url.isEmpty()) return;
        Toast.makeText(this, R.string.image_preparing_share, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File cacheDir = new File(getCacheDir(), "shared_images");
                if (!cacheDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    cacheDir.mkdirs();
                }
                String ext = getExtensionFromUrl(url);
                File file = new File(cacheDir, "share_" + System.currentTimeMillis() + "." + ext);
                boolean ok;
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    ok = downloadToStream(url, fos);
                }
                if (ok) {
                    Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(getMimeFromExtension(ext));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, url);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    runOnUiThread(() -> startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_image))));
                } else {
                    runOnUiThread(() -> Toast.makeText(this, R.string.image_share_failed, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.chat_send_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show());
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
        builder.setTitle(R.string.image_details_title);
        String initialMsg = getString(R.string.image_details_format, url, ext, getString(R.string.image_details_calculating));
        builder.setMessage(initialMsg);
        builder.setPositiveButton(R.string.action_ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();

        new Thread(() -> {
            long size = getRemoteFileSize(url);
            String sizeStr = size > 0 ? Formatter.formatFileSize(this, size) : getString(R.string.unknown);
            runOnUiThread(() -> {
                if (dialog.isShowing()) {
                    String updatedMsg = getString(R.string.image_details_format, url, ext, sizeStr);
                    dialog.setMessage(updatedMsg);
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
            } else {
                r.close();
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private boolean downloadToStream(String url, OutputStream os) {
        try {
            String fullUrl = normalizeUrl(url);
            String cleanUrl = stripQiniuParams(fullUrl);
            Request.Builder b = new Request.Builder().url(cleanUrl);
            if (cleanUrl.contains(".jwznb.com")) {
                b.header("Referer", "https://myapp.jwznb.com");
                b.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
            }
            Response r = ApiClient.getClient().newCall(b.build()).execute();
            if ((!r.isSuccessful() || r.body() == null) && !cleanUrl.equals(fullUrl)) {
                b = new Request.Builder().url(fullUrl);
                if (fullUrl.contains(".jwznb.com")) {
                    b.header("Referer", "https://myapp.jwznb.com");
                    b.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
                }
                r = ApiClient.getClient().newCall(b.build()).execute();
            }
            if (!r.isSuccessful() || r.body() == null) return false;
            try (InputStream is = r.body().byteStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
            } finally {
                r.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getExtensionFromUrl(String url) {
        if (url == null) return "jpg";
        String clean = stripQiniuParams(url);
        String lower = clean.toLowerCase(Locale.getDefault());
        if (lower.contains(".gif")) return "gif";
        if (lower.contains(".png")) return "png";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "jpg";
        if (lower.contains(".webp")) return "webp";
        if (lower.contains(".avif")) return "avif";
        if (lower.contains(".bmp")) return "bmp";
        if (lower.contains(".tmp") || lower.contains("/expression/")) return "gif";
        if (lower.contains("/sticker/")) return "png";
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
            Log.e(TAG, "Failed to decode sampled bitmap", e);
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
