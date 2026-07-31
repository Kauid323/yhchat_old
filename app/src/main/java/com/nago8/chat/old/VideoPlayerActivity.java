package com.nago8.chat.old;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.net.FileDownloadManager;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_VIDEO_TITLE = "video_title";
    public static final String REFERER_HEADER_VALUE = "http://myapp.jwznb.com";

    private View rootView;
    private View topBar;
    private View bottomControls;
    private VideoView videoView;
    private ProgressBar progressBar;
    private AppCompatImageButton btnPlayPause;
    private AppCompatImageButton btnMute;
    private AppCompatImageButton btnLoop;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;

    private MediaPlayer mediaPlayer;
    private Call downloadCall;
    private File loadedFile;
    private String videoUrl;
    private String videoTitle;

    private boolean isMuted = false;
    private boolean isLooping = false;
    private boolean isFullscreen = false;
    private boolean isTrackingProgress = false;
    private boolean isControlsShowing = true;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && videoView.isPlaying() && !isTrackingProgress) {
                int current = videoView.getCurrentPosition();
                int duration = videoView.getDuration();
                if (duration > 0) {
                    seekBar.setProgress((int) ((current * 100L) / duration));
                    tvCurrentTime.setText(formatTime(current));
                    tvTotalTime.setText(formatTime(duration));
                }
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    @SuppressLint("SourceLockedOrientationActivity")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);

        rootView = findViewById(R.id.rootView);
        topBar = findViewById(R.id.topBar);
        bottomControls = findViewById(R.id.bottomControls);
        videoView = findViewById(R.id.videoView);
        progressBar = findViewById(R.id.progressBar);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnMute = findViewById(R.id.btnMute);
        btnLoop = findViewById(R.id.btnLoop);
        AppCompatImageButton btnRotate = findViewById(R.id.btnRotate);
        AppCompatImageButton btnFullscreen = findViewById(R.id.btnFullscreen);
        AppCompatImageButton btnInfo = findViewById(R.id.btnInfo);
        AppCompatImageButton btnDownload = findViewById(R.id.btnDownload);

        seekBar = findViewById(R.id.seekBar);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        TextView tvTitle = findViewById(R.id.tvTitle);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> onBackPressed());

        if (videoTitle != null && !videoTitle.isEmpty()) {
            tvTitle.setText(videoTitle);
        }

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "无效的视频播放地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        videoView.setOnPreparedListener(mp -> {
            mediaPlayer = mp;
            progressBar.setVisibility(View.GONE);
            mp.setLooping(isLooping);
            if (isMuted) {
                mp.setVolume(0f, 0f);
            } else {
                mp.setVolume(1f, 1f);
            }
            videoView.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            handler.post(updateProgressRunnable);
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(VideoPlayerActivity.this, "视频播放失败", Toast.LENGTH_SHORT).show();
            return true;
        });

        videoView.setOnCompletionListener(mp -> {
            if (!isLooping) {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(100);
            }
        });

        btnPlayPause.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                btnPlayPause.setImageResource(R.drawable.ic_play);
            } else {
                videoView.start();
                btnPlayPause.setImageResource(R.drawable.ic_pause);
            }
        });

        // 1. 音量开启 / 屏蔽
        btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(isMuted ? 0f : 1f, isMuted ? 0f : 1f);
            }
            btnMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
            Toast.makeText(this, isMuted ? "已静音" : "已开启音量", Toast.LENGTH_SHORT).show();
        });

        // 2. 循环播放
        btnLoop.setOnClickListener(v -> {
            isLooping = !isLooping;
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(isLooping);
            }
            btnLoop.setColorFilter(isLooping ? Color.WHITE : Color.parseColor("#80FFFFFF"));
            Toast.makeText(this, isLooping ? "开启循环播放" : "关闭循环播放", Toast.LENGTH_SHORT).show();
        });

        // 3. 旋转按钮（横屏 / 竖屏）
        btnRotate.setOnClickListener(v -> {
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        });

        // 4. 全屏切换按钮
        if (btnFullscreen != null) {
            btnFullscreen.setOnClickListener(v -> toggleFullscreen());
        }

        View touchOverlay = findViewById(R.id.touchOverlay);
        if (touchOverlay != null) {
            touchOverlay.setOnClickListener(v -> toggleControlsVisibility());
        }
        if (rootView != null) {
            rootView.setOnClickListener(v -> toggleControlsVisibility());
        }

        // 5. 点击 Info 显示视频详细信息
        btnInfo.setOnClickListener(v -> showVideoInfoDialog());

        // 6. 点击下载按钮
        btnDownload.setOnClickListener(v -> downloadVideo());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoView.getDuration() > 0) {
                    int seekTo = (videoView.getDuration() * progress) / 100;
                    tvCurrentTime.setText(formatTime(seekTo));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTrackingProgress = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (videoView.getDuration() > 0) {
                    int seekTo = (videoView.getDuration() * seekBar.getProgress()) / 100;
                    videoView.seekTo(seekTo);
                }
                isTrackingProgress = false;
            }
        });

        showControls();
        loadAndPlayVideo(videoUrl);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("SourceLockedOrientationActivity")
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            hideSystemUI();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            showSystemUI();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        showControls();
    }

    private void toggleControlsVisibility() {
        if (isControlsShowing) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        isControlsShowing = true;
        topBar.setVisibility(View.VISIBLE);
        bottomControls.setVisibility(View.VISIBLE);
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, 4000);
    }

    private void hideControls() {
        isControlsShowing = false;
        topBar.setVisibility(View.GONE);
        bottomControls.setVisibility(View.GONE);
        handler.removeCallbacks(autoHideRunnable);
    }

    private final Runnable autoHideRunnable = this::hideControls;

    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @SuppressWarnings("deprecation")
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void downloadVideo() {
        if (videoUrl == null || videoUrl.isEmpty()) return;
        Toast.makeText(this, "开始下载视频...", Toast.LENGTH_SHORT).show();
        String name = videoTitle != null && !videoTitle.isEmpty() ? videoTitle : "video_" + System.currentTimeMillis() + ".mp4";
        FileDownloadManager.getInstance().download(this, videoUrl, name, new FileDownloadManager.DownloadCallback() {
            @Override
            public void onProgress(int percent) {}

            @Override
            public void onComplete(File file) {
                runOnUiThread(() -> Toast.makeText(VideoPlayerActivity.this, "视频已保存至: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(VideoPlayerActivity.this, "下载失败: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onCancel() {}
        });
    }

    @SuppressWarnings("SpellCheckingInspection")
    private void showVideoInfoDialog() {
        if (loadedFile == null || !loadedFile.exists()) {
            Toast.makeText(this, "正在获取视频数据，请稍后...", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();

        String videoCodec = "H.264 / AVC";
        String widthStr = "未知";
        String frameRateStr = "30 fps";
        String bitrateStr = "未知";
        String fileSizeStr = Formatter.formatFileSize(this, loadedFile.length());

        String audioCodec = "AAC";
        String channelsStr = "2 (双声道)";
        String sampleRateStr = "44100 Hz";
        String audioBitrateStr = "128 kbps";
        String decoderStr = "Android Native MediaCodec";

        MediaMetadataRetriever retriever = null;
        MediaExtractor extractor = null;

        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(loadedFile.getAbsolutePath());

            String w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (w != null && h != null) {
                widthStr = w + " x " + h;
            } else if (videoView != null && videoView.getWidth() > 0) {
                widthStr = videoView.getWidth() + " x " + videoView.getHeight();
            }

            String br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (br != null) {
                long brLong = Long.parseLong(br);
                bitrateStr = (brLong / 1000) + " kbps";
            }

            String mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (mime != null) {
                if (mime.contains("avc") || mime.contains("mp4")) {
                    videoCodec = "H.264 / AVC";
                } else if (mime.contains("hevc") || mime.contains("265")) {
                    videoCodec = "H.265 / HEVC";
                } else {
                    videoCodec = mime;
                }
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(loadedFile.getAbsolutePath());
            int trackCount = extractor.getTrackCount();
            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String trackMime = format.containsKey(MediaFormat.KEY_MIME) ? format.getString(MediaFormat.KEY_MIME) : "";
                if (trackMime != null && trackMime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRateStr = format.getInteger(MediaFormat.KEY_FRAME_RATE) + " fps";
                    }
                } else if (trackMime != null && trackMime.startsWith("audio/")) {
                    if (trackMime.contains("mp4a") || trackMime.contains("aac")) {
                        audioCodec = "AAC";
                    } else {
                        audioCodec = trackMime;
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        channelsStr = channels == 1 ? "1 (单声道)" : (channels == 2 ? "2 (双声道/立体声)" : channels + " 声道");
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRateStr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE) + " Hz";
                    }
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        audioBitrateStr = (format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000) + " kbps";
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (retriever != null) {
                try { retriever.release(); } catch (Exception ignored) {}
            }
            if (extractor != null) {
                try { extractor.release(); } catch (Exception ignored) {}
            }
        }

        sb.append("📹 视频信息\n");
        sb.append(" • 视频编码：").append(videoCodec).append("\n");
        sb.append(" • 分辨率：").append(widthStr).append("\n");
        sb.append(" • 帧率：").append(frameRateStr).append("\n");
        sb.append(" • 比特率：").append(bitrateStr).append("\n");
        sb.append(" • 文件大小：").append(fileSizeStr).append("\n\n");

        sb.append("🎵 音频信息\n");
        sb.append(" • 音频编码：").append(audioCodec).append("\n");
        sb.append(" • 声道数：").append(channelsStr).append("\n");
        sb.append(" • 采样率：").append(sampleRateStr).append("\n");
        sb.append(" • 音频比特率：").append(audioBitrateStr).append("\n\n");

        sb.append("⚙️ 解码器\n");
        sb.append(" • 解码组件：").append(decoderStr);

        new AlertDialog.Builder(this)
                .setTitle("视频详细信息")
                .setMessage(sb.toString())
                .setPositiveButton("确定", null)
                .show();
    }

    private void setVideoUriCompat(String url) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER_HEADER_VALUE);
            videoView.setVideoURI(Uri.parse(url), headers);
        } else {
            videoView.setVideoURI(Uri.parse(url));
        }
    }

    private void loadAndPlayVideo(String videoUrl) {
        if (videoUrl != null && !videoUrl.startsWith("http://") && !videoUrl.startsWith("https://")) {
            loadedFile = new File(videoUrl);
            videoView.setVideoPath(videoUrl);
            return;
        }

        File cacheDir = getCacheDir();
        String fileName = md5(videoUrl) + ".mp4";
        File targetFile = new File(cacheDir, fileName);

        if (targetFile.exists() && targetFile.length() > 0) {
            loadedFile = targetFile;
            videoView.setVideoPath(targetFile.getAbsolutePath());
            return;
        }

        Request.Builder reqBuilder = new Request.Builder()
                .url(videoUrl)
                .header("Referer", REFERER_HEADER_VALUE);

        String token = PrefUtils.getToken(this);
        if (token != null && !token.isEmpty()) {
            reqBuilder.header("token", token);
        }

        downloadCall = ApiClient.getClient().newCall(reqBuilder.build());
        downloadCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "downloadCall failed", e);
                runOnUiThread(() -> setVideoUriCompat(videoUrl));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> setVideoUriCompat(videoUrl));
                    return;
                }

                File tmpFile = new File(cacheDir, fileName + ".tmp");
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(tmpFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.flush();
                    boolean renamed = tmpFile.renameTo(targetFile);
                    if (!renamed) {
                        Log.w(TAG, "renameTo targetFile failed");
                    }

                    loadedFile = targetFile;
                    runOnUiThread(() -> videoView.setVideoPath(targetFile.getAbsolutePath()));
                } catch (Exception e) {
                    Log.e(TAG, "cache video file error", e);
                    runOnUiThread(() -> setVideoUriCompat(videoUrl));
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
    }

    private String md5(String string) {
        if (string == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(string.getBytes(StandardCharsets.UTF_8));
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                if (h.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(string.hashCode());
        }
    }

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
        handler.removeCallbacks(updateProgressRunnable);
    }

    @Override
    protected void onDestroy() {
        if (downloadCall != null) {
            downloadCall.cancel();
        }
        if (videoView != null) {
            videoView.stopPlayback();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
