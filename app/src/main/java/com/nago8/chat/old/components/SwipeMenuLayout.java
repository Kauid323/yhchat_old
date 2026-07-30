package com.nago8.chat.old.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

public class SwipeMenuLayout extends ViewGroup {

    private View contentView;
    private View menuView;

    private int touchSlop;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;

    private Scroller scroller;
    private static SwipeMenuLayout mOpenInstance;

    public SwipeMenuLayout(Context context) {
        this(context, null);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scroller = new Scroller(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() >= 2) {
            contentView = getChildAt(0);
            menuView = getChildAt(1);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (contentView == null || menuView == null) return;

        measureChild(contentView, widthMeasureSpec, heightMeasureSpec);
        measureChild(menuView, widthMeasureSpec, heightMeasureSpec);

        int height = contentView.getMeasuredHeight();
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);

        int menuWidth = menuView.getMeasuredWidth();
        menuView.measure(
                MeasureSpec.makeMeasureSpec(menuWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (contentView == null || menuView == null) return;

        int width = r - l;
        int height = b - t;

        contentView.layout(0, 0, width, height);
        int menuWidth = menuView.getMeasuredWidth();
        menuView.layout(width, 0, width + menuWidth, height);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                lastX = downX;
                lastY = downY;
                if (mOpenInstance != null && mOpenInstance != this) {
                    mOpenInstance.smoothClose();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - downX;
                float dy = ev.getRawY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - lastX;
                float dy = ev.getRawY() - lastY;
                if (Math.abs(dx) > Math.abs(dy)) {
                    scrollBy((int) -dx, 0);
                    int menuWidth = menuView != null ? menuView.getMeasuredWidth() : 0;
                    if (getScrollX() < 0) {
                        scrollTo(0, 0);
                    } else if (getScrollX() > menuWidth) {
                        scrollTo(menuWidth, 0);
                    }
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                lastX = ev.getRawX();
                lastY = ev.getRawY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                int menuWidth = menuView != null ? menuView.getMeasuredWidth() : 0;
                if (getScrollX() > menuWidth / 3) {
                    smoothOpen();
                } else {
                    smoothClose();
                }
                break;
        }
        return true;
    }

    public void smoothOpen() {
        if (menuView == null) return;
        int menuWidth = menuView.getMeasuredWidth();
        int dx = menuWidth - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, 200);
        invalidate();
        mOpenInstance = this;
    }

    public void smoothClose() {
        int dx = -getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, 200);
        invalidate();
        if (mOpenInstance == this) {
            mOpenInstance = null;
        }
    }

    public static void closeOpenInstance() {
        if (mOpenInstance != null) {
            mOpenInstance.smoothClose();
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
            postInvalidate();
        }
    }
}
