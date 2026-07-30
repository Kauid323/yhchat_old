package com.nago8.chat.old.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewConfiguration;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    private static final float MID_SCALE = 2.5f;
    private static final float MAX_SCALE = 6.0f;

    private final Matrix matrix = new Matrix();

    private float currentScale = 1.0f;
    private float baseScale = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;

    private int viewWidth = 0;
    private int viewHeight = 0;
    private int drawableWidth = 0;
    private int drawableHeight = 0;

    private float lastX, lastY;
    private int lastPointerCount = 0;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean dragging = false;
    private float touchSlop;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private boolean imageReady = false;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();

                float newScale = currentScale * scaleFactor;
                if (newScale < baseScale) {
                    scaleFactor = baseScale / currentScale;
                    newScale = baseScale;
                } else if (newScale > MAX_SCALE * baseScale) {
                    scaleFactor = (MAX_SCALE * baseScale) / currentScale;
                    newScale = MAX_SCALE * baseScale;
                }

                translateX = focusX - (focusX - translateX) * scaleFactor;
                translateY = focusY - (focusY - translateY) * scaleFactor;
                currentScale = newScale;

                constrainTranslation();
                applyMatrix();
                return true;
            }
        });
        // Keep a one-finger drag reserved for panning. Android's quick-scale
        // gesture otherwise treats a double-tap followed by a drag as zoom.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            scaleDetector.setQuickScaleEnabled(false);
        }

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float targetScale = (currentScale > baseScale + 0.01f) ? baseScale : MID_SCALE * baseScale;
                zoomTo(targetScale, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return true;
            }
        });
    }

    private void zoomTo(float targetScale, float focusX, float focusY) {
        float scaleFactor = targetScale / currentScale;
        translateX = focusX - (focusX - translateX) * scaleFactor;
        translateY = focusY - (focusY - translateY) * scaleFactor;
        currentScale = targetScale;
        constrainTranslation();
        applyMatrix();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!imageReady) return false;

        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        final int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);
                lastX = event.getX(0);
                lastY = event.getY(0);
                lastPointerCount = 1;
                dragging = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger belongs to the scale detector. Do not carry
                // its movement into the one-finger pan when it is lifted.
                lastPointerCount = pointerCount;
                dragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1 && currentScale > baseScale + 0.01f) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex < 0) pointerIndex = 0;

                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);
                    float dx = x - lastX;
                    float dy = y - lastY;

                    if (!dragging && (dx * dx + dy * dy) >= touchSlop * touchSlop) {
                        dragging = true;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    if (dragging) {
                        translateX += dx;
                        translateY += dy;
                        constrainTranslation();
                        applyMatrix();
                    }
                    lastX = x;
                    lastY = y;
                } else if (pointerCount != lastPointerCount) {
                    lastX = event.getX(0);
                    lastY = event.getY(0);
                }
                lastPointerCount = pointerCount;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // Rebase the next one-finger drag at the remaining pointer.
                if (pointerCount > 1) {
                    int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
                    activePointerId = event.getPointerId(remainingIndex);
                    lastX = event.getX(remainingIndex);
                    lastY = event.getY(remainingIndex);
                }
                lastPointerCount = pointerCount - 1;
                dragging = false;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                lastPointerCount = 0;
                dragging = false;
                break;
        }

        return true;
    }

    private void constrainTranslation() {
        float scaledW = drawableWidth * currentScale;
        float scaledH = drawableHeight * currentScale;

        float centerX = (viewWidth - scaledW) / 2f;
        float centerY = (viewHeight - scaledH) / 2f;

        float maxX, minX, maxY, minY;

        if (scaledW <= viewWidth) {
            minX = maxX = centerX;
        } else {
            minX = viewWidth - scaledW - centerX;
            maxX = centerX;
        }

        if (scaledH <= viewHeight) {
            minY = maxY = centerY;
        } else {
            minY = viewHeight - scaledH - centerY;
            maxY = centerY;
        }

        if (translateX < minX) translateX = minX;
        if (translateX > maxX) translateX = maxX;
        if (translateY < minY) translateY = minY;
        if (translateY > maxY) translateY = maxY;
    }

    private void applyMatrix() {
        matrix.reset();
        matrix.postScale(currentScale, currentScale);
        matrix.postTranslate(translateX, translateY);
        setImageMatrix(matrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        calculateBaseScale();
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        if (bm != null) {
            drawableWidth = bm.getWidth();
            drawableHeight = bm.getHeight();
            calculateBaseScale();
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (drawable != null) {
            Rect bounds = drawable.getBounds();
            drawableWidth = drawable.getIntrinsicWidth();
            drawableHeight = drawable.getIntrinsicHeight();
            if (drawableWidth > 0 && drawableHeight > 0) {
                calculateBaseScale();
            }
        }
    }

    private void calculateBaseScale() {
        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) return;

        float scaleX = (float) viewWidth / drawableWidth;
        float scaleY = (float) viewHeight / drawableHeight;
        baseScale = Math.min(scaleX, scaleY);

        currentScale = baseScale;
        translateX = (viewWidth - drawableWidth * baseScale) / 2f;
        translateY = (viewHeight - drawableHeight * baseScale) / 2f;
        imageReady = true;
        applyMatrix();
    }

    public void resetZoom() {
        if (!imageReady) return;
        currentScale = baseScale;
        translateX = (viewWidth - drawableWidth * baseScale) / 2f;
        translateY = (viewHeight - drawableHeight * baseScale) / 2f;
        applyMatrix();
    }
}
