package com.nago8.chat.old.widget;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nago8.chat.old.R;
import com.nago8.chat.old.StickerPackDetailActivity;
import com.nago8.chat.old.StickerPackManagerActivity;
import com.nago8.chat.old.adapter.EmojiGridAdapter;
import com.nago8.chat.old.adapter.ExpressionGridAdapter;
import com.nago8.chat.old.adapter.StickerGridAdapter;
import com.nago8.chat.old.cache.StickerMemoryCache;
import com.nago8.chat.old.model.EmojiData;
import com.nago8.chat.old.model.Expression;
import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.model.StickerPack;
import com.nago8.chat.old.repository.ExpressionRepository;
import com.nago8.chat.old.repository.StickerRepository;
import com.nago8.chat.old.utils.FengEmojiRenderer;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EmojiPanelLayout extends LinearLayout {

    public interface OnEmojiSelectedListener {
        void onEmojiSelected(String emojiText);
    }

    public interface OnStickerSelectedListener {
        void onStickerSelected(StickerItem stickerItem);
    }

    public interface OnBackspaceClickListener {
        void onBackspaceClick();
    }

    public interface OnManageClickListener {
        void onManageClick();
    }

    private ViewPager viewPagerEmoji;
    private HorizontalScrollView scrollTabs;
    private LinearLayout layoutTabsContainer;
    private View btnBackspace;
    private View btnManageStickers;
    private View layoutDragHandle;

    private TextView tvEmojiUnicodeSubTab;
    private TextView tvEmojiTwemojiSubTab;
    private RecyclerView rvEmojiSubTab;
    private EmojiGridAdapter unicodeEmojiAdapter;
    private EmojiGridAdapter twemojiEmojiAdapter;

    private int minHeightPx;
    private ValueAnimator heightAnimator;
    private int touchSlop;
    private VelocityTracker velocityTracker;
    private float touchDownRawX;
    private float touchDownRawY;
    private float dragInitialRawY;
    private int dragInitialHeight = 0;
    private long touchDownTime;
    private boolean isPanelDragging = false;
    private boolean isDraggingFromHandle = false;

    private final List<Expression> favoriteExpressions = new ArrayList<>();
    private final List<StickerPack> stickerPacks = new ArrayList<>();
    private final SparseArray<View> cachedPageViews = new SparseArray<>();
    private final List<View> tabViews = new ArrayList<>();

    private OnEmojiSelectedListener emojiSelectedListener;
    private OnStickerSelectedListener stickerSelectedListener;
    private OnBackspaceClickListener backspaceClickListener;
    private OnManageClickListener manageClickListener;

    private final ExpressionRepository expressionRepository = new ExpressionRepository();
    private final StickerRepository stickerRepository = new StickerRepository();

    public EmojiPanelLayout(Context context) {
        super(context);
        init(context);
    }

    public EmojiPanelLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EmojiPanelLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.layout_emoji_panel, this, true);

        viewPagerEmoji = findViewById(R.id.viewPagerEmoji);
        scrollTabs = findViewById(R.id.scrollTabs);
        layoutTabsContainer = findViewById(R.id.layoutTabsContainer);
        btnBackspace = findViewById(R.id.btnBackspace);
        btnManageStickers = findViewById(R.id.btnManageStickers);
        layoutDragHandle = findViewById(R.id.layoutDragHandle);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        minHeightPx = dp(280);

        if (viewPagerEmoji != null) {
            viewPagerEmoji.setOffscreenPageLimit(1);
        }

        if (layoutDragHandle != null) {
            layoutDragHandle.setOnClickListener(v -> toggleExpand());
        }

        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                if (backspaceClickListener != null) {
                    backspaceClickListener.onBackspaceClick();
                }
            });
        }

        if (btnManageStickers != null) {
            btnManageStickers.setOnClickListener(v -> {
                if (manageClickListener != null) {
                    manageClickListener.onManageClick();
                } else {
                    Intent intent = new Intent(getContext(), StickerPackManagerActivity.class);
                    getContext().startActivity(intent);
                }
            });
        }

        viewPagerEmoji.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                updateSelectedTab(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        // 优先从内存会话缓存加载
        if (StickerMemoryCache.isInitialized()) {
            favoriteExpressions.clear();
            favoriteExpressions.addAll(StickerMemoryCache.getFavoriteExpressions());
            stickerPacks.clear();
            stickerPacks.addAll(StickerMemoryCache.getStickerPacks());
            reloadTabs();
        } else {
            reloadTabs();
            reloadStickers(false);
        }
    }

    private void acquireVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        int curH = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
        int maxH = getMaxHeightPx();
        // 如果正在拖拽，或手指在把手上，或面板尚未完全展开，禁止子列表抢夺拦截
        if (isPanelDragging || isDraggingFromHandle || curH < maxH) {
            return;
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchDownRawX = ev.getRawX();
                touchDownRawY = ev.getRawY();
                dragInitialRawY = ev.getRawY();
                touchDownTime = System.currentTimeMillis();
                dragInitialHeight = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
                isPanelDragging = false;

                int handleH = (layoutDragHandle != null && layoutDragHandle.getHeight() > 0)
                        ? layoutDragHandle.getHeight() : dp(30);
                isDraggingFromHandle = (ev.getY() <= handleH);

                if (heightAnimator != null && heightAnimator.isRunning()) {
                    heightAnimator.cancel();
                }
                acquireVelocityTracker();
                velocityTracker.addMovement(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                acquireVelocityTracker();
                velocityTracker.addMovement(ev);

                if (isPanelDragging) {
                    return true;
                }

                float dx = ev.getRawX() - touchDownRawX;
                float dy = ev.getRawY() - touchDownRawY;

                // 水平滑动切换 Tab / Page 时不拦截
                if (Math.abs(dx) > Math.abs(dy)) {
                    return false;
                }

                if (Math.abs(dy) > touchSlop) {
                    int curH = getHeight() > 0 ? getHeight() : dragInitialHeight;
                    int maxH = getMaxHeightPx();
                    int minH = minHeightPx;

                    // 1. 如果手指在顶部拖拽条上滑动，直接拦截接管拖拽
                    if (isDraggingFromHandle) {
                        isPanelDragging = true;
                        dragInitialHeight = curH;
                        dragInitialRawY = ev.getRawY();
                        return true;
                    }

                    // 2. 面板未完全展开时，向上滑动（dy < 0）直接拦截并向上拉伸展开面板
                    if (dy < 0 && curH < maxH) {
                        isPanelDragging = true;
                        dragInitialHeight = curH;
                        dragInitialRawY = ev.getRawY();
                        return true;
                    }

                    // 3. 向下滑动（dy > 0）：若当前面板高于 minH 且内部列表已处于最顶部，拦截并开始收缩面板
                    if (dy > 0 && curH > minH) {
                        RecyclerView rv = getCurrentPageRecyclerView();
                        if (rv == null || !rv.canScrollVertically(-1)) {
                            isPanelDragging = true;
                            dragInitialHeight = curH;
                            dragInitialRawY = ev.getRawY();
                            return true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isPanelDragging = false;
                isDraggingFromHandle = false;
                recycleVelocityTracker();
                break;
        }
        return isPanelDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        acquireVelocityTracker();
        velocityTracker.addMovement(ev);

        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchDownRawX = ev.getRawX();
                touchDownRawY = ev.getRawY();
                dragInitialRawY = ev.getRawY();
                touchDownTime = System.currentTimeMillis();
                dragInitialHeight = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
                isPanelDragging = false;
                int handleH = (layoutDragHandle != null && layoutDragHandle.getHeight() > 0)
                        ? layoutDragHandle.getHeight() : dp(30);
                isDraggingFromHandle = (ev.getY() <= handleH);

                if (heightAnimator != null && heightAnimator.isRunning()) {
                    heightAnimator.cancel();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float rawY = ev.getRawY();
                if (!isPanelDragging) {
                    float totalDy = rawY - touchDownRawY;
                    float totalDx = ev.getRawX() - touchDownRawX;
                    if (Math.abs(totalDy) > touchSlop && Math.abs(totalDy) > Math.abs(totalDx)) {
                        isPanelDragging = true;
                        dragInitialHeight = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
                        dragInitialRawY = rawY;
                    }
                }

                if (isPanelDragging) {
                    float deltaY = dragInitialRawY - rawY; // 向上拖拽 rawY变小，deltaY为正
                    int targetH = (int) (dragInitialHeight + deltaY);
                    int maxH = getMaxHeightPx();
                    int minH = minHeightPx;

                    int clampedH = Math.max(minH, Math.min(maxH, targetH));
                    setPanelHeight(clampedH);

                    // 越界时调整基准点 dragInitialRawY，实现反向滑动零死区即时响应，且绝不破坏原始基准
                    if (targetH > maxH) {
                        dragInitialRawY = rawY + (maxH - dragInitialHeight);
                    } else if (targetH < minH) {
                        dragInitialRawY = rawY + (minH - dragInitialHeight);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
                acquireVelocityTracker();
                velocityTracker.computeCurrentVelocity(1000);
                float vy = velocityTracker.getYVelocity();

                long duration = System.currentTimeMillis() - touchDownTime;
                float totalMoved = (float) Math.hypot(ev.getRawX() - touchDownRawX, ev.getRawY() - touchDownRawY);

                if (isDraggingFromHandle && totalMoved < touchSlop && duration < 300) {
                    toggleExpand();
                } else if (isPanelDragging) {
                    int curH = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
                    int maxH = getMaxHeightPx();
                    int minH = minHeightPx;

                    if (vy < -1200) {
                        // 快速向上猛甩：平滑展开至最大高度
                        animateToHeight(maxH);
                    } else if (vy > 1200) {
                        // 快速向下猛甩：平滑收缩至默认高度
                        animateToHeight(minH);
                    } else {
                        // 随心所欲自由悬停：松手停在当前任意高度，超出安全边界才回弹
                        if (curH < minH) {
                            animateToHeight(minH);
                        } else if (curH > maxH) {
                            animateToHeight(maxH);
                        }
                    }
                }
                isPanelDragging = false;
                isDraggingFromHandle = false;
                recycleVelocityTracker();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (isPanelDragging) {
                    int curH = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
                    int maxH = getMaxHeightPx();
                    int minH = minHeightPx;
                    if (curH < minH) {
                        animateToHeight(minH);
                    } else if (curH > maxH) {
                        animateToHeight(maxH);
                    }
                }
                isPanelDragging = false;
                isDraggingFromHandle = false;
                recycleVelocityTracker();
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private RecyclerView getCurrentPageRecyclerView() {
        if (viewPagerEmoji == null) return null;
        int currentItem = viewPagerEmoji.getCurrentItem();
        View page = cachedPageViews.get(currentItem);
        if (page instanceof RecyclerView) {
            return (RecyclerView) page;
        } else if (page instanceof ViewGroup) {
            return findRecyclerView((ViewGroup) page);
        }
        return null;
    }

    // ======================================================================================

    /**
     * 精确计算面板可拉伸的最大高度（恰好使聊天输入框触碰顶栏/上传进度条底部，且底栏严密贴合屏幕底缘不溢出）
     */
    public int getMaxHeightPx() {
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            View contentView = activity.findViewById(android.R.id.content);
            if (contentView != null && contentView.getHeight() > 0) {
                View topBar = activity.findViewById(R.id.topBar);
                View uploadProgress = activity.findViewById(R.id.layoutUploadProgress);
                int topBoundary;
                if (uploadProgress != null && uploadProgress.getVisibility() == VISIBLE && uploadProgress.getBottom() > 0) {
                    topBoundary = uploadProgress.getBottom();
                } else if (topBar != null && topBar.getBottom() > 0) {
                    topBoundary = topBar.getBottom();
                } else {
                    int topBarH = (topBar != null && topBar.getHeight() > 0) ? topBar.getHeight() : dp(56);
                    int uploadH = (uploadProgress != null && uploadProgress.getVisibility() == VISIBLE)
                            ? (uploadProgress.getHeight() > 0 ? uploadProgress.getHeight() : dp(32)) : 0;
                    topBoundary = topBarH + uploadH;
                }

                int nonPanelH = dp(56);
                View chatInputBar = activity.findViewById(R.id.chatInputBar);
                if (chatInputBar instanceof com.nago8.chat.old.components.ChatInputBar) {
                    nonPanelH = ((com.nago8.chat.old.components.ChatInputBar) chatInputBar).getNonPanelHeight();
                } else if (chatInputBar instanceof ViewGroup) {
                    ViewGroup inputGroup = (ViewGroup) chatInputBar;
                    nonPanelH = inputGroup.getPaddingTop() + inputGroup.getPaddingBottom();
                    View inputRow = inputGroup.findViewById(R.id.layoutInputRow);
                    nonPanelH += (inputRow != null && inputRow.getHeight() > 0) ? inputRow.getHeight() : dp(56);
                }

                int calculatedMax = contentView.getHeight() - topBoundary - nonPanelH;
                if (calculatedMax > minHeightPx) {
                    return calculatedMax;
                }
            }
        }
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        return Math.max(minHeightPx, screenHeight - dp(184));
    }

    public int getMinHeightPx() {
        return minHeightPx;
    }

    public void setMinHeightPx(int minHeightPx) {
        this.minHeightPx = minHeightPx;
    }

    public void reloadStickers() {
        reloadStickers(false);
    }

    /**
     * 加载表情包及个人表情收藏数据（带会话内存缓存与冷启动网络同步）
     */
    public void reloadStickers(boolean forceRefresh) {
        String token = PrefUtils.getToken(getContext());
        if (TextUtils.isEmpty(token)) {
            reloadTabs();
            return;
        }

        if (!forceRefresh && StickerMemoryCache.isInitialized()) {
            favoriteExpressions.clear();
            favoriteExpressions.addAll(StickerMemoryCache.getFavoriteExpressions());
            stickerPacks.clear();
            stickerPacks.addAll(StickerMemoryCache.getStickerPacks());
            reloadTabs();
            return;
        }

        // 1. 加载个人表情收藏 (POST /v1/expression/list)
        expressionRepository.listExpressions(token, new ExpressionRepository.ExpressionsCallback() {
            @Override
            public void onSuccess(List<Expression> expressions) {
                post(() -> {
                    favoriteExpressions.clear();
                    if (expressions != null) {
                        favoriteExpressions.addAll(expressions);
                    }
                    StickerMemoryCache.setFavoriteExpressions(favoriteExpressions);
                    reloadTabs();
                });
            }

            @Override
            public void onError(Exception error) {}
        });

        // 2. 加载表情包列表 (POST /v1/sticker/list)
        stickerRepository.listStickerPacks(token, new StickerRepository.StickerPacksCallback() {
            @Override
            public void onSuccess(List<StickerPack> packs) {
                post(() -> {
                    stickerPacks.clear();
                    if (packs != null) {
                        stickerPacks.addAll(packs);
                    }
                    StickerMemoryCache.setStickerPacks(stickerPacks);
                    reloadTabs();
                });
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    private void reloadTabs() {
        int previousPage = viewPagerEmoji != null ? viewPagerEmoji.getCurrentItem() : 0;
        cachedPageViews.clear();
        tabViews.clear();
        layoutTabsContainer.removeAllViews();

        Context ctx = getContext();

        // 1. Tab 0: 默认 Emoji + Twemoji 页面
        addTextTab(ctx, "😀", 0);

        // 2. Tab 1: 我的表情收藏页面
        addTextTab(ctx, "❤️", 1);

        // 3. Tab 2..N: 各个表情包页面
        for (int i = 0; i < stickerPacks.size(); i++) {
            final int pageIndex = i + 2;
            StickerPack pack = stickerPacks.get(i);
            addStickerPackTab(ctx, pack, pageIndex);
        }

        viewPagerEmoji.setAdapter(new EmojiPagerAdapter());
        int totalPages = 2 + stickerPacks.size();
        if (previousPage > 0 && previousPage < totalPages) {
            viewPagerEmoji.setCurrentItem(previousPage, false);
            updateSelectedTab(previousPage);
        } else {
            applyDefaultOrRandomSelection();
        }
    }

    public void switchEmojiSubTab(boolean isTwemoji) {
        if (tvEmojiUnicodeSubTab == null || tvEmojiTwemojiSubTab == null || rvEmojiSubTab == null) return;
        Context ctx = getContext();
        if (isTwemoji) {
            tvEmojiTwemojiSubTab.setTextColor(ThemeUtils.getThemeColor(ctx));
            tvEmojiUnicodeSubTab.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            if (twemojiEmojiAdapter != null) {
                rvEmojiSubTab.setAdapter(twemojiEmojiAdapter);
            }
        } else {
            tvEmojiUnicodeSubTab.setTextColor(ThemeUtils.getThemeColor(ctx));
            tvEmojiTwemojiSubTab.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            if (unicodeEmojiAdapter != null) {
                rvEmojiSubTab.setAdapter(unicodeEmojiAdapter);
            }
        }
    }

    public void applyDefaultOrRandomSelection() {
        Context ctx = getContext();
        if (ctx == null || viewPagerEmoji == null) return;

        int totalPages = (viewPagerEmoji.getAdapter() != null) ? viewPagerEmoji.getAdapter().getCount() : (2 + stickerPacks.size());
        int targetPage = 0;
        boolean isTwemoji = false;

        if (PrefUtils.isEmojiRandomPackEnabled(ctx) && !stickerPacks.isEmpty()) {
            int rand = new Random().nextInt(stickerPacks.size());
            targetPage = 2 + rand;
        } else {
            String type = PrefUtils.getEmojiDefaultTabType(ctx);
            if (PrefUtils.EMOJI_TAB_TWEMOJI.equals(type)) {
                targetPage = 0;
                isTwemoji = true;
            } else if (PrefUtils.EMOJI_TAB_FAVORITE.equals(type)) {
                targetPage = 1;
            } else if (PrefUtils.EMOJI_TAB_PACK.equals(type)) {
                long packId = PrefUtils.getEmojiDefaultPackId(ctx);
                int foundIndex = -1;
                for (int i = 0; i < stickerPacks.size(); i++) {
                    if (stickerPacks.get(i).id == packId) {
                        foundIndex = i;
                        break;
                    }
                }
                if (foundIndex >= 0) {
                    targetPage = 2 + foundIndex;
                } else {
                    targetPage = 0;
                }
            } else {
                targetPage = 0;
                isTwemoji = false;
            }
        }

        if (targetPage >= totalPages) {
            targetPage = 0;
        }

        if (targetPage == 0) {
            switchEmojiSubTab(isTwemoji);
        }

        viewPagerEmoji.setCurrentItem(targetPage, false);
        updateSelectedTab(targetPage);
    }

    private View createEmojiPageView(Context ctx) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(VERTICAL);

        // 顶部 Emoji 切换子 Tab（Unicode / Twemoji）
        LinearLayout subTabRow = new LinearLayout(ctx);
        subTabRow.setOrientation(HORIZONTAL);
        subTabRow.setGravity(Gravity.CENTER_VERTICAL);
        subTabRow.setPadding(dp(12), dp(4), dp(12), dp(4));

        TextView tvUnicode = new TextView(ctx);
        tvUnicode.setText(R.string.emoji_tab_unicode);
        tvUnicode.setTextSize(13);
        tvUnicode.setPadding(dp(12), dp(4), dp(12), dp(4));
        tvUnicode.setTextColor(ThemeUtils.getThemeColor(ctx));
        tvUnicode.setBackgroundResource(R.drawable.bg_item_ripple);

        TextView tvTwemoji = new TextView(ctx);
        tvTwemoji.setText(R.string.emoji_tab_twemoji);
        tvTwemoji.setTextSize(13);
        tvTwemoji.setPadding(dp(12), dp(4), dp(12), dp(4));
        tvTwemoji.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        tvTwemoji.setBackgroundResource(R.drawable.bg_item_ripple);

        subTabRow.addView(tvUnicode);
        subTabRow.addView(tvTwemoji);
        layout.addView(subTabRow);

        RecyclerView recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new GridLayoutManager(ctx, 7));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(true);
        recyclerView.setItemViewCacheSize(35);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        recyclerView.setPadding(dp(8), dp(4), dp(8), dp(8));
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final EmojiGridAdapter unicodeAdapter = new EmojiGridAdapter(ctx, EmojiData.UNICODE_EMOJIS, false, emojiText -> {
            if (emojiSelectedListener != null) {
                emojiSelectedListener.onEmojiSelected(emojiText);
            }
        });

        final List<String> twemojiList = EmojiData.getTwemojiTokens(ctx);
        final EmojiGridAdapter twemojiAdapter = new EmojiGridAdapter(ctx, twemojiList, true, emojiText -> {
            if (emojiSelectedListener != null) {
                emojiSelectedListener.onEmojiSelected(emojiText);
            }
        });

        this.tvEmojiUnicodeSubTab = tvUnicode;
        this.tvEmojiTwemojiSubTab = tvTwemoji;
        this.rvEmojiSubTab = recyclerView;
        this.unicodeEmojiAdapter = unicodeAdapter;
        this.twemojiEmojiAdapter = twemojiAdapter;

        boolean isDefaultTwemoji = PrefUtils.EMOJI_TAB_TWEMOJI.equals(PrefUtils.getEmojiDefaultTabType(ctx))
                && !PrefUtils.isEmojiRandomPackEnabled(ctx);
        if (isDefaultTwemoji) {
            tvTwemoji.setTextColor(ThemeUtils.getThemeColor(ctx));
            tvUnicode.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            recyclerView.setAdapter(twemojiAdapter);
        } else {
            tvUnicode.setTextColor(ThemeUtils.getThemeColor(ctx));
            tvTwemoji.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            recyclerView.setAdapter(unicodeAdapter);
        }

        tvUnicode.setOnClickListener(v -> switchEmojiSubTab(false));
        tvTwemoji.setOnClickListener(v -> switchEmojiSubTab(true));

        layout.addView(recyclerView);
        return layout;
    }

    private View createFavoriteExpressionsPageView(Context ctx) {
        if (favoriteExpressions.isEmpty()) {
            TextView tvEmpty = new TextView(ctx);
            tvEmpty.setText(R.string.emoji_favorite_empty_tip);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setLineSpacing(dp(4), 1f);
            tvEmpty.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            tvEmpty.setPadding(dp(24), dp(32), dp(24), dp(32));
            return tvEmpty;
        }

        RecyclerView recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new GridLayoutManager(ctx, 4));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(true);
        recyclerView.setItemViewCacheSize(30);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        recyclerView.setPadding(dp(12), dp(10), dp(12), dp(10));
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ExpressionGridAdapter adapter = new ExpressionGridAdapter(ctx, favoriteExpressions, new ExpressionGridAdapter.OnExpressionClickListener() {
            @Override
            public void onExpressionClick(Expression expression) {
                if (stickerSelectedListener != null && expression != null) {
                    StickerItem item = new StickerItem(expression.id, "expression", expression.url, 0);
                    stickerSelectedListener.onStickerSelected(item);
                }
            }

            @Override
            public void onExpressionLongClick(Expression expression) {
                showExpressionActionDialog(ctx, expression);
            }
        });

        recyclerView.setAdapter(adapter);
        return recyclerView;
    }

    private void showExpressionActionDialog(Context ctx, Expression expression) {
        String[] options = {ctx.getString(R.string.emoji_action_top), ctx.getString(R.string.emoji_action_delete)};
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.emoji_action_title)
                .setItems(options, (dialog, which) -> {
                    String token = PrefUtils.getToken(ctx);
                    if (TextUtils.isEmpty(token)) return;

                    if (which == 0) {
                        expressionRepository.topExpression(token, expression.id, new ExpressionRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                post(() -> {
                                    Toast.makeText(ctx, R.string.emoji_topped, Toast.LENGTH_SHORT).show();
                                    reloadStickers(true);
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                post(() -> Toast.makeText(ctx, error.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        });
                    } else if (which == 1) {
                        expressionRepository.deleteExpression(token, expression.id, new ExpressionRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                post(() -> {
                                    Toast.makeText(ctx, R.string.emoji_deleted, Toast.LENGTH_SHORT).show();
                                    reloadStickers(true);
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                post(() -> Toast.makeText(ctx, error.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        });
                    }
                })
                .show();
    }

    private View createStickerPackPageView(Context ctx, StickerPack pack) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部小巧 Header Row：左边表情包名称，右边箭头，点击直接进入表情包详情页
        LinearLayout headerRow = new LinearLayout(ctx);
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(14), dp(5), dp(14), dp(3));
        headerRow.setBackgroundResource(R.drawable.bg_item_ripple);
        headerRow.setClickable(true);
        headerRow.setFocusable(true);

        TextView tvPackName = new TextView(ctx);
        tvPackName.setText(pack != null && !TextUtils.isEmpty(pack.name) ? pack.name : "");
        tvPackName.setTextSize(12);
        tvPackName.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        tvPackName.setMaxLines(1);
        tvPackName.setEllipsize(TextUtils.TruncateAt.END);
        tvPackName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView ivArrow = new ImageView(ctx);
        ivArrow.setImageResource(R.drawable.ic_chevron_right);
        int arrowSize = dp(14);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(arrowSize, arrowSize);
        arrowLp.setMarginStart(dp(4));
        ivArrow.setLayoutParams(arrowLp);
        ivArrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivArrow.setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary));

        headerRow.addView(tvPackName);
        headerRow.addView(ivArrow);

        if (pack != null) {
            headerRow.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, StickerPackDetailActivity.class);
                intent.putExtra(StickerPackDetailActivity.EXTRA_PACK_ID, pack.id);
                ctx.startActivity(intent);
            });
        }

        container.addView(headerRow);

        RecyclerView recyclerView = new RecyclerView(ctx);
        recyclerView.setLayoutManager(new GridLayoutManager(ctx, 4));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(true);
        recyclerView.setItemViewCacheSize(30);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        recyclerView.setPadding(dp(12), dp(4), dp(12), dp(10));
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        List<StickerItem> cachedItems = (pack != null) ? StickerMemoryCache.getPackItems(pack.id) : null;
        if (cachedItems != null && !cachedItems.isEmpty()) {
            pack.stickerItems = cachedItems;
        }
        List<StickerItem> items = (pack != null && pack.stickerItems != null) ? pack.stickerItems : new ArrayList<>();
        StickerGridAdapter adapter = new StickerGridAdapter(ctx, items, new StickerGridAdapter.OnStickerClickListener() {
            @Override
            public void onStickerClick(StickerItem item) {
                if (stickerSelectedListener != null) {
                    stickerSelectedListener.onStickerSelected(item);
                }
            }

            @Override
            public void onStickerLongClick(StickerItem item) {}
        });

        recyclerView.setAdapter(adapter);

        if (pack != null && items.isEmpty()) {
            String token = PrefUtils.getToken(ctx);
            if (!TextUtils.isEmpty(token)) {
                stickerRepository.getStickerPackDetail(token, pack.id, new StickerRepository.StickerPackDetailCallback() {
                    @Override
                    public void onSuccess(StickerPack detailPack) {
                        if (detailPack != null && detailPack.stickerItems != null && !detailPack.stickerItems.isEmpty()) {
                            if (pack.stickerItems == null) {
                                pack.stickerItems = new ArrayList<>();
                            }
                            pack.stickerItems.clear();
                            pack.stickerItems.addAll(detailPack.stickerItems);
                            StickerMemoryCache.putPackItems(pack.id, pack.stickerItems);
                            post(() -> {
                                if (tvPackName != null && !TextUtils.isEmpty(detailPack.name)) {
                                    tvPackName.setText(detailPack.name);
                                }
                                adapter.setItems(pack.stickerItems);
                            });
                        }
                    }

                    @Override
                    public void onError(Exception error) {}
                });
            }
        }

        container.addView(recyclerView);
        return container;
    }

    public void smoothScrollCurrentPageToTop() {
        int cur = viewPagerEmoji.getCurrentItem();
        View page = cachedPageViews.get(cur);
        if (page != null) {
            if (page instanceof RecyclerView) {
                ((RecyclerView) page).smoothScrollToPosition(0);
            } else if (page instanceof ViewGroup) {
                RecyclerView rv = findRecyclerView((ViewGroup) page);
                if (rv != null) {
                    rv.smoothScrollToPosition(0);
                }
            }
        }
    }

    private RecyclerView findRecyclerView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof RecyclerView) {
                return (RecyclerView) child;
            } else if (child instanceof ViewGroup) {
                RecyclerView r = findRecyclerView((ViewGroup) child);
                if (r != null) return r;
            }
        }
        return null;
    }

    public void toggleExpand() {
        int maxH = getMaxHeightPx();
        int curH = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
        int mid = minHeightPx + (maxH - minHeightPx) / 2;
        animateToHeight(curH >= mid ? minHeightPx : maxH);
    }

    private void animateToHeight(int targetH) {
        if (heightAnimator != null && heightAnimator.isRunning()) {
            heightAnimator.cancel();
        }
        int curH = getHeight() > 0 ? getHeight() : (getLayoutParams() != null ? getLayoutParams().height : minHeightPx);
        if (curH == targetH) return;
        heightAnimator = ValueAnimator.ofInt(curH, targetH);
        heightAnimator.setDuration(260);
        heightAnimator.setInterpolator(new DecelerateInterpolator(2.0f));
        heightAnimator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            setPanelHeight(val);
        });
        heightAnimator.start();
    }

    public void setPanelHeight(int height) {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            if (lp.height != height) {
                lp.height = height;
                setLayoutParams(lp);
            }
        } else {
            setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        }
    }

    private void addTextTab(Context ctx, String text, int targetPageIndex) {
        LinearLayout tab = new LinearLayout(ctx);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(14), 0, dp(14), 0);
        tab.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.setBackgroundResource(R.drawable.bg_item_ripple);

        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(16);
        tab.addView(tv);

        tab.setOnClickListener(v -> {
            if (viewPagerEmoji.getCurrentItem() == targetPageIndex) {
                smoothScrollCurrentPageToTop();
            } else {
                viewPagerEmoji.setCurrentItem(targetPageIndex, true);
            }
        });
        layoutTabsContainer.addView(tab);
        tabViews.add(tab);
    }

    private void addStickerPackTab(Context ctx, StickerPack pack, int targetPageIndex) {
        LinearLayout tab = new LinearLayout(ctx);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(10), 0, dp(10), 0);
        tab.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.setBackgroundResource(R.drawable.bg_item_ripple);

        String coverUrl = pack.getCoverUrl();
        if (!TextUtils.isEmpty(coverUrl)) {
            ImageView ivCover = new ImageView(ctx);
            int size = dp(24);
            ivCover.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            ivCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ImageUtils.loadSticker(ctx, coverUrl, ivCover, 48, 48);
            tab.addView(ivCover);
        } else {
            TextView tvName = new TextView(ctx);
            tvName.setText(pack.name != null ? pack.name : "");
            tvName.setTextSize(12);
            tvName.setMaxLines(1);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            tvName.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            tab.addView(tvName);
        }

        tab.setOnClickListener(v -> {
            if (viewPagerEmoji.getCurrentItem() == targetPageIndex) {
                smoothScrollCurrentPageToTop();
            } else {
                viewPagerEmoji.setCurrentItem(targetPageIndex, true);
            }
        });
        layoutTabsContainer.addView(tab);
        tabViews.add(tab);
    }

    private void updateSelectedTab(int selectedPosition) {
        int themeColor = ThemeUtils.getThemeColor(getContext());
        for (int i = 0; i < tabViews.size(); i++) {
            View tab = tabViews.get(i);
            if (i == selectedPosition) {
                tab.setBackgroundColor((themeColor & 0x00FFFFFF) | 0x22000000);
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        if (selectedPosition >= 0 && selectedPosition < tabViews.size() && scrollTabs != null) {
            View tab = tabViews.get(selectedPosition);
            scrollTabs.post(() -> {
                int scrollX = tab.getLeft() - (scrollTabs.getWidth() - tab.getWidth()) / 2;
                scrollTabs.smoothScrollTo(Math.max(0, scrollX), 0);
            });
        }
    }

    public void setOnEmojiSelectedListener(OnEmojiSelectedListener listener) {
        this.emojiSelectedListener = listener;
    }

    public void setOnStickerSelectedListener(OnStickerSelectedListener listener) {
        this.stickerSelectedListener = listener;
    }

    public void setOnBackspaceClickListener(OnBackspaceClickListener listener) {
        this.backspaceClickListener = listener;
    }

    public void setOnManageClickListener(OnManageClickListener listener) {
        this.manageClickListener = listener;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class EmojiPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return 2 + stickerPacks.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view = cachedPageViews.get(position);
            if (view == null) {
                Context ctx = container.getContext();
                if (position == 0) {
                    view = createEmojiPageView(ctx);
                } else if (position == 1) {
                    view = createFavoriteExpressionsPageView(ctx);
                } else {
                    int packIndex = position - 2;
                    if (packIndex >= 0 && packIndex < stickerPacks.size()) {
                        view = createStickerPackPageView(ctx, stickerPacks.get(packIndex));
                    } else {
                        view = new View(ctx);
                    }
                }
                cachedPageViews.put(position, view);
            }
            if (view.getParent() == null) {
                container.addView(view);
            }
            return view;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }
    }
}
