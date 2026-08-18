package com.nago8.chat.old.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 超轻量、高性能语音进度条/滑块控件
 * 特性：
 * 1. 纯 Canvas 硬件加速直绘，onDraw 零内存分配（无 GC 抖动，Dalvik/Android 4.4 满帧 60fps）；
 * 2. 圆点 Thumb 永远绝对居中于轨道中线，两端完美贴合，永不错位；
 * 3. 支持点击跳转与丝滑拖拽快进/快退。
 */
public class VoiceProgressSlider extends View {

    public interface OnSeekChangeListener {
        void onProgressChanged(VoiceProgressSlider slider, int progress, boolean fromUser);
        void onStartTrackingTouch(VoiceProgressSlider slider);
        void onStopTrackingTouch(VoiceProgressSlider slider, int progress);
    }

    private final Paint inactiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF inactiveRect = new RectF();
    private final RectF activeRect = new RectF();

    private int max = 1000;
    private int progress = 0;

    private float trackHeight;
    private float thumbRadius;

    private boolean isTracking = false;
    private OnSeekChangeListener seekChangeListener;

    public VoiceProgressSlider(Context context) {
        this(context, null);
    }

    public VoiceProgressSlider(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VoiceProgressSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        trackHeight = 3.0f * density;
        thumbRadius = 5.0f * density;

        // 默认配色
        inactiveTrackPaint.setColor(0x40FFFFFF);
        activeTrackPaint.setColor(0xFFFFFFFF);
        thumbPaint.setColor(0xFFFFFFFF);
    }

    /**
     * 设置颜色方案
     */
    public void setColors(int activeColor, int inactiveColor, int thumbColor) {
        activeTrackPaint.setColor(activeColor);
        inactiveTrackPaint.setColor(inactiveColor);
        thumbPaint.setColor(thumbColor);
        invalidate();
    }

    public void setColors(int primaryColor, boolean isMine) {
        if (isMine) {
            setColors(0xFFFFFFFF, 0x40FFFFFF, 0xFFFFFFFF);
        } else {
            int inactive = (primaryColor & 0x00FFFFFF) | 0x26000000; // ~15% 透明度
            setColors(primaryColor, inactive, primaryColor);
        }
    }

    public void setTrackHeightDp(float dp) {
        trackHeight = dp * getResources().getDisplayMetrics().density;
        invalidate();
    }

    public void setThumbRadiusDp(float dp) {
        thumbRadius = dp * getResources().getDisplayMetrics().density;
        invalidate();
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = Math.max(1, max);
        if (progress > this.max) {
            progress = this.max;
        }
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        if (isTracking) return; // 用户正在拖动时不被外部打断
        int newProgress = Math.max(0, Math.min(progress, max));
        if (this.progress != newProgress) {
            this.progress = newProgress;
            invalidate();
        }
    }

    public void setOnSeekChangeListener(OnSeekChangeListener listener) {
        this.seekChangeListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int minHeight = (int) (Math.max(thumbRadius * 2, trackHeight) + 12 * density);
        int height = resolveSize(minHeight, heightMeasureSpec);
        int width = resolveSize((int) (120 * density), widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cy = h / 2.0f;
        float paddingLeft = thumbRadius;
        float paddingRight = w - thumbRadius;
        float trackWidth = Math.max(0, paddingRight - paddingLeft);

        float trackTop = cy - trackHeight / 2.0f;
        float trackBottom = cy + trackHeight / 2.0f;
        float trackCorner = trackHeight / 2.0f;

        // 1. 底轨 (Inactive Track)
        inactiveRect.set(paddingLeft, trackTop, paddingRight, trackBottom);
        canvas.drawRoundRect(inactiveRect, trackCorner, trackCorner, inactiveTrackPaint);

        // 2. 进度轨 (Active Track) & 圆点位置
        float ratio = max > 0 ? (float) progress / max : 0f;
        ratio = Math.max(0f, Math.min(1f, ratio));
        float thumbX = paddingLeft + trackWidth * ratio;

        if (ratio > 0f) {
            activeRect.set(paddingLeft, trackTop, thumbX, trackBottom);
            canvas.drawRoundRect(activeRect, trackCorner, trackCorner, activeTrackPaint);
        }

        // 3. 圆点 (Thumb) - 绝对居中于中线
        canvas.drawCircle(thumbX, cy, thumbRadius, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        float x = event.getX();
        int w = getWidth();
        float paddingLeft = thumbRadius;
        float paddingRight = w - thumbRadius;
        float trackWidth = Math.max(1, paddingRight - paddingLeft);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isTracking = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateProgressFromTouch(x, paddingLeft, trackWidth);
                if (seekChangeListener != null) {
                    seekChangeListener.onStartTrackingTouch(this);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                updateProgressFromTouch(x, paddingLeft, trackWidth);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTracking = false;
                updateProgressFromTouch(x, paddingLeft, trackWidth);
                if (seekChangeListener != null) {
                    seekChangeListener.onStopTrackingTouch(this, progress);
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateProgressFromTouch(float x, float paddingLeft, float trackWidth) {
        float ratio = (x - paddingLeft) / trackWidth;
        ratio = Math.max(0f, Math.min(1f, ratio));
        int newProgress = (int) (ratio * max);
        if (newProgress != progress) {
            progress = newProgress;
            invalidate();
            if (seekChangeListener != null) {
                seekChangeListener.onProgressChanged(this, progress, true);
            }
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
