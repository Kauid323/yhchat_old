package com.nago8.chat.old.widget;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nago8.chat.old.R;
import com.nago8.chat.old.StickerPackManagerActivity;
import com.nago8.chat.old.adapter.EmojiGridAdapter;
import com.nago8.chat.old.adapter.ExpressionGridAdapter;
import com.nago8.chat.old.adapter.StickerGridAdapter;
import com.nago8.chat.old.model.EmojiData;
import com.nago8.chat.old.model.Expression;
import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.model.StickerPack;
import com.nago8.chat.old.repository.ExpressionRepository;
import com.nago8.chat.old.repository.StickerRepository;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

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
    private LinearLayout layoutTabsContainer;
    private View btnBackspace;
    private View btnManageStickers;
    private View layoutDragHandle;

    private int minHeightPx;
    private ValueAnimator heightAnimator;

    private final List<Expression> favoriteExpressions = new ArrayList<>();
    private final List<StickerPack> stickerPacks = new ArrayList<>();
    private final List<View> pageViews = new ArrayList<>();
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
        layoutTabsContainer = findViewById(R.id.layoutTabsContainer);
        btnBackspace = findViewById(R.id.btnBackspace);
        btnManageStickers = findViewById(R.id.btnManageStickers);
        layoutDragHandle = findViewById(R.id.layoutDragHandle);

        minHeightPx = dp(320);

        if (layoutDragHandle != null) {
            layoutDragHandle.setOnTouchListener(new OnTouchListener() {
                private float startY;
                private int startHeight;
                private boolean isDragging = false;
                private long touchDownTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    int maxH = getMaxHeightPx();
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = event.getRawY();
                            startHeight = getHeight();
                            touchDownTime = System.currentTimeMillis();
                            isDragging = false;
                            if (heightAnimator != null && heightAnimator.isRunning()) {
                                heightAnimator.cancel();
                            }
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float rawY = event.getRawY();
                            float deltaFromStart = rawY - startY;
                            if (!isDragging && Math.abs(deltaFromStart) > dp(4)) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                int newH = (int) Math.max(minHeightPx, Math.min(maxH, startHeight - deltaFromStart));
                                setPanelHeight(newH);
                                return true;
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                            long duration = System.currentTimeMillis() - touchDownTime;
                            float totalMoved = Math.abs(event.getRawY() - startY);
                            if (!isDragging || (totalMoved < dp(6) && duration < 300)) {
                                toggleExpand();
                            } else {
                                checkEdgeSnap();
                            }
                            isDragging = false;
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            isDragging = false;
                            break;
                    }
                    return false;
                }
            });
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

        // 默认初始化 Emoji 页面
        reloadTabs();
    }

    /**
     * 计算面板可拉伸的最大高度（恰好使聊天输入框触碰顶栏底部）
     */
    public int getMaxHeightPx() {
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            View contentView = activity.findViewById(android.R.id.content);
            if (contentView != null && contentView.getHeight() > 0) {
                View topBar = activity.findViewById(R.id.topBar);
                int topBarH = (topBar != null && topBar.getHeight() > 0) ? topBar.getHeight() : dp(56);

                View chatInputBar = activity.findViewById(R.id.chatInputBar);
                int otherInputH = dp(56);
                if (chatInputBar instanceof ViewGroup) {
                    ViewGroup inputGroup = (ViewGroup) chatInputBar;
                    int curPanelH = (getVisibility() == VISIBLE) ? getHeight() : 0;
                    if (inputGroup.getHeight() > curPanelH) {
                        otherInputH = inputGroup.getHeight() - curPanelH;
                    }
                }
                int calculatedMax = contentView.getHeight() - topBarH - otherInputH;
                if (calculatedMax > minHeightPx) {
                    return calculatedMax;
                }
            }
        }
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        return Math.max(minHeightPx + dp(120), screenHeight - dp(120));
    }

    public int getMinHeightPx() {
        return minHeightPx;
    }

    /**
     * 重新加载表情包及个人表情收藏数据
     */
    public void reloadStickers() {
        String token = PrefUtils.getToken(getContext());
        if (TextUtils.isEmpty(token)) {
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
                    reloadTabs();
                });
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    private void reloadTabs() {
        int previousPage = viewPagerEmoji.getCurrentItem();
        pageViews.clear();
        tabViews.clear();
        layoutTabsContainer.removeAllViews();

        Context ctx = getContext();

        // 1. Page 0: 默认 Emoji + Twemoji 页面
        View emojiPageView = createEmojiPageView(ctx);
        pageViews.add(emojiPageView);
        addTextTab(ctx, "😀", 0);

        // 2. Page 1: 我的表情收藏页面
        View favPageView = createFavoriteExpressionsPageView(ctx);
        pageViews.add(favPageView);
        addTextTab(ctx, "❤️", 1);

        // 3. Page 2..N: 各个表情包页面
        for (int i = 0; i < stickerPacks.size(); i++) {
            final int pageIndex = i + 2;
            StickerPack pack = stickerPacks.get(i);
            View packPageView = createStickerPackPageView(ctx, pack);
            pageViews.add(packPageView);
            addStickerPackTab(ctx, pack, pageIndex);
        }

        viewPagerEmoji.setAdapter(new EmojiPagerAdapter(pageViews));
        int safePage = Math.min(previousPage, pageViews.size() - 1);
        viewPagerEmoji.setCurrentItem(Math.max(0, safePage), false);
        updateSelectedTab(viewPagerEmoji.getCurrentItem());
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

        GridView gridView = new GridView(ctx);
        gridView.setNumColumns(7);
        gridView.setGravity(Gravity.CENTER);
        gridView.setVerticalSpacing(dp(6));
        gridView.setHorizontalSpacing(dp(4));
        gridView.setPadding(dp(8), dp(4), dp(8), dp(8));
        gridView.setClipToPadding(false);
        gridView.setScrollbarFadingEnabled(true);
        gridView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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

        gridView.setAdapter(unicodeAdapter);
        attachScrollExpansionToGrid(gridView);

        tvUnicode.setOnClickListener(v -> {
            tvUnicode.setTextColor(ThemeUtils.getThemeColor(ctx));
            tvTwemoji.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            gridView.setAdapter(unicodeAdapter);
        });

        tvTwemoji.setOnClickListener(v -> {
            tvTwemoji.setTextColor(ThemeUtils.getThemeColor(ctx));
            tvUnicode.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            gridView.setAdapter(twemojiAdapter);
        });

        layout.addView(gridView);
        return layout;
    }

    private View createFavoriteExpressionsPageView(Context ctx) {
        if (favoriteExpressions.isEmpty()) {
            TextView tvEmpty = new TextView(ctx);
            tvEmpty.setText("暂无收藏的表情\n长按聊天中的图片添加到表情收藏");
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setLineSpacing(dp(4), 1f);
            tvEmpty.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            tvEmpty.setPadding(dp(24), dp(32), dp(24), dp(32));
            return tvEmpty;
        }

        GridView gridView = new GridView(ctx);
        gridView.setNumColumns(4);
        gridView.setGravity(Gravity.CENTER);
        gridView.setVerticalSpacing(dp(8));
        gridView.setHorizontalSpacing(dp(8));
        gridView.setPadding(dp(12), dp(10), dp(12), dp(10));
        gridView.setClipToPadding(false);

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

        gridView.setAdapter(adapter);
        attachScrollExpansionToGrid(gridView);
        return gridView;
    }

    private void showExpressionActionDialog(Context ctx, Expression expression) {
        String[] options = {"置顶表情", "删除表情"};
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("表情操作")
                .setItems(options, (dialog, which) -> {
                    String token = PrefUtils.getToken(ctx);
                    if (TextUtils.isEmpty(token)) return;

                    if (which == 0) {
                        expressionRepository.topExpression(token, expression.id, new ExpressionRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                post(() -> {
                                    Toast.makeText(ctx, "已置顶", Toast.LENGTH_SHORT).show();
                                    reloadStickers();
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
                                    Toast.makeText(ctx, "已删除", Toast.LENGTH_SHORT).show();
                                    reloadStickers();
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
        GridView gridView = new GridView(ctx);
        gridView.setNumColumns(4);
        gridView.setGravity(Gravity.CENTER);
        gridView.setVerticalSpacing(dp(8));
        gridView.setHorizontalSpacing(dp(8));
        gridView.setPadding(dp(12), dp(10), dp(12), dp(10));
        gridView.setClipToPadding(false);

        List<StickerItem> items = pack.stickerItems != null ? pack.stickerItems : new ArrayList<>();
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

        gridView.setAdapter(adapter);
        attachScrollExpansionToGrid(gridView);

        if (items.isEmpty()) {
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
                            post(() -> adapter.setItems(pack.stickerItems));
                        }
                    }

                    @Override
                    public void onError(Exception error) {}
                });
            }
        }

        return gridView;
    }

    private void attachScrollExpansionToGrid(GridView gridView) {
        if (gridView == null) return;
        gridView.setOnTouchListener(new OnTouchListener() {
            private float startY;
            private float lastY;
            private boolean isDraggingPanel = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int maxH = getMaxHeightPx();
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        lastY = startY;
                        isDraggingPanel = false;
                        if (heightAnimator != null && heightAnimator.isRunning()) {
                            heightAnimator.cancel();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float rawY = event.getRawY();
                        float deltaY = rawY - lastY;
                        lastY = rawY;

                        boolean isAtTop = isGridAtTop(gridView);
                        int curH = getHeight();

                        // 只有在顶部向下拉或者在未完全展开时向上推，才联动调整面板高度
                        if (deltaY < 0 && curH < maxH && isAtTop) {
                            int newH = (int) Math.min(maxH, curH - deltaY);
                            setPanelHeight(newH);
                            isDraggingPanel = true;
                            return true;
                        } else if (deltaY > 0 && isAtTop && curH > minHeightPx) {
                            int newH = (int) Math.max(minHeightPx, curH - deltaY);
                            setPanelHeight(newH);
                            isDraggingPanel = true;
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDraggingPanel) {
                            isDraggingPanel = false;
                            checkEdgeSnap();
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    private boolean isGridAtTop(GridView gridView) {
        if (gridView == null || gridView.getChildCount() == 0) return true;
        return gridView.getFirstVisiblePosition() == 0 && gridView.getChildAt(0).getTop() >= gridView.getPaddingTop();
    }

    public void toggleExpand() {
        int maxH = getMaxHeightPx();
        int curH = getHeight();
        int mid = minHeightPx + (maxH - minHeightPx) / 2;
        animateToHeight(curH >= mid ? minHeightPx : maxH);
    }

    private void checkEdgeSnap() {
        int maxH = getMaxHeightPx();
        int curH = getHeight();
        int snapMargin = dp(24);
        if (curH <= minHeightPx + snapMargin) {
            animateToHeight(minHeightPx);
        } else if (curH >= maxH - snapMargin) {
            animateToHeight(maxH);
        }
    }

    private void animateToHeight(int targetH) {
        if (heightAnimator != null && heightAnimator.isRunning()) {
            heightAnimator.cancel();
        }
        int curH = getHeight();
        if (curH == targetH) return;
        heightAnimator = ValueAnimator.ofInt(curH, targetH);
        heightAnimator.setDuration(220);
        heightAnimator.setInterpolator(new DecelerateInterpolator());
        heightAnimator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            setPanelHeight(val);
        });
        heightAnimator.start();
    }

    public void setPanelHeight(int height) {
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            setLayoutParams(lp);
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

        tab.setOnClickListener(v -> viewPagerEmoji.setCurrentItem(targetPageIndex, true));
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

        tab.setOnClickListener(v -> viewPagerEmoji.setCurrentItem(targetPageIndex, true));
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

    private static class EmojiPagerAdapter extends PagerAdapter {
        private final List<View> views;

        public EmojiPagerAdapter(List<View> views) {
            this.views = views;
        }

        @Override
        public int getCount() {
            return views.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view = views.get(position);
            container.addView(view);
            return view;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }
    }
}
