package com.nago8.chat.old.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nago8.chat.old.R;
import com.nago8.chat.old.StickerPackManagerActivity;
import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.utils.HtmlNativeRenderer;
import com.nago8.chat.old.widget.EmojiPanelLayout;

/**
 * 独立的消息输入框组件（位于 /components/ 文件夹）
 * 支持左侧加号展开扩展面板、表情按钮展开 Emoji/Sticker 面板、指令按钮与引用预览。
 */
public class ChatInputBar extends LinearLayout {

    public interface OnSendClickListener {
        void onSendClick(String text);
    }

    public interface OnPanelActionClickListener {
        void onActionClick(String actionType);
    }

    public interface OnInstructionButtonClickListener {
        void onInstructionButtonClick();
    }

    public interface OnStickerSendListener {
        void onStickerSend(StickerItem stickerItem);
    }

    public interface OnQuoteDismissListener {
        void onQuoteDismissed();
    }

    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnTogglePanel;
    private ImageButton btnInstruction;
    private ImageButton btnEmoji;
    private View panelMore;
    private EmojiPanelLayout panelEmoji;

    private boolean isPanelExpanded = false;
    private boolean isEmojiPanelExpanded = false;
    private ValueAnimator panelMoreAnimator;
    private ValueAnimator panelEmojiAnimator;
    private int cachedPanelMoreHeight = 0;

    private OnSendClickListener sendClickListener;
    private OnPanelActionClickListener panelActionClickListener;
    private OnInstructionButtonClickListener instructionButtonClickListener;
    private OnStickerSendListener stickerSendListener;
    private OnQuoteDismissListener quoteDismissListener;

    // Quote preview bar (shown above the input row when replying)
    private LinearLayout quotePreviewBar;
    private TextView tvQuotePreviewText;
    private Msg pendingQuoteMsg;

    public ChatInputBar(Context context) {
        super(context);
        init(context);
    }

    public ChatInputBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ChatInputBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setBackgroundResource(R.color.card_background);

        // --- Quote preview bar (inflated first so it appears above the input row) ---
        buildQuotePreviewBar(context);
        addView(quotePreviewBar, 0);

        LayoutInflater.from(context).inflate(R.layout.layout_chat_input_bar, this, true);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnTogglePanel = findViewById(R.id.btnTogglePanel);
        btnInstruction = findViewById(R.id.btnInstruction);
        btnEmoji = findViewById(R.id.btnEmoji);
        panelMore = findViewById(R.id.panelMore);
        panelEmoji = findViewById(R.id.panelEmoji);

        if (btnTogglePanel != null) {
            btnTogglePanel.setOnClickListener(v -> togglePanel());
        }

        if (btnEmoji != null) {
            btnEmoji.setOnClickListener(v -> toggleEmojiPanel());
        }

        if (btnInstruction != null) {
            btnInstruction.setOnClickListener(v -> {
                if (instructionButtonClickListener != null) {
                    instructionButtonClickListener.onInstructionButtonClick();
                }
            });
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> {
                String text = getInputText();
                if (text.length() > 0 && sendClickListener != null) {
                    collapseAllPanels();
                    sendClickListener.onSendClick(text);
                }
            });
            btnSend.setAlpha(0.4f);
            btnSend.setEnabled(false);
        }

        if (etMessage != null) {
            etMessage.setOnClickListener(v -> {
                if (isPanelExpanded || isEmojiPanelExpanded) {
                    collapseAllPanelsImmediately();
                }
            });
            etMessage.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && (isPanelExpanded || isEmojiPanelExpanded)) {
                    collapseAllPanelsImmediately();
                }
            });
            etMessage.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    boolean hasText = s != null && s.toString().trim().length() > 0;
                    if (btnSend != null) {
                        btnSend.setAlpha(hasText ? 1.0f : 0.4f);
                        btnSend.setEnabled(hasText);
                    }
                }
            });
        }

        setupEmojiPanel();
        setupPanelActions();
    }

    private void setupEmojiPanel() {
        if (panelEmoji == null) return;

        panelEmoji.setOnEmojiSelectedListener(emojiText -> {
            if (etMessage == null || TextUtils.isEmpty(emojiText)) return;
            int start = Math.max(etMessage.getSelectionStart(), 0);
            int end = Math.max(etMessage.getSelectionEnd(), 0);
            etMessage.getText().replace(Math.min(start, end), Math.max(start, end),
                    emojiText, 0, emojiText.length());
        });

        panelEmoji.setOnStickerSelectedListener(stickerItem -> {
            if (stickerSendListener != null && stickerItem != null) {
                stickerSendListener.onStickerSend(stickerItem);
            }
        });

        panelEmoji.setOnBackspaceClickListener(() -> {
            if (etMessage == null) return;
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
            etMessage.dispatchKeyEvent(event);
        });

        panelEmoji.setOnManageClickListener(() -> {
            Context ctx = getContext();
            if (ctx != null) {
                Intent intent = new Intent(ctx, StickerPackManagerActivity.class);
                ctx.startActivity(intent);
            }
        });
    }

    // ---- Quote preview bar construction ----

    private void buildQuotePreviewBar(Context ctx) {
        quotePreviewBar = new LinearLayout(ctx);
        quotePreviewBar.setOrientation(HORIZONTAL);
        quotePreviewBar.setGravity(Gravity.CENTER_VERTICAL);
        quotePreviewBar.setVisibility(GONE);
        int hPad = dp(ctx, 12);
        int vPad = dp(ctx, 6);
        quotePreviewBar.setPadding(hPad, vPad, hPad, vPad);
        quotePreviewBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.chat_background));
        quotePreviewBar.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Left accent bar
        View accentBar = new View(ctx);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(ctx, 3), dp(ctx, 30));
        accentParams.rightMargin = dp(ctx, 8);
        accentBar.setLayoutParams(accentParams);
        accentBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.app_primary));
        quotePreviewBar.addView(accentBar);

        // Quote text
        tvQuotePreviewText = new TextView(ctx);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvQuotePreviewText.setLayoutParams(textParams);
        tvQuotePreviewText.setMaxLines(2);
        tvQuotePreviewText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvQuotePreviewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvQuotePreviewText.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        quotePreviewBar.addView(tvQuotePreviewText);

        // Close button
        TextView btnClose = new TextView(ctx);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.leftMargin = dp(ctx, 8);
        btnClose.setLayoutParams(closeParams);
        btnClose.setText("✕");
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnClose.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        btnClose.setPadding(dp(ctx, 4), 0, dp(ctx, 4), 0);
        btnClose.setOnClickListener(v -> clearQuote());
        quotePreviewBar.addView(btnClose);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    /** Show the quote preview bar for the given message. */
    public void showQuotePreview(Msg msg) {
        if (msg == null) return;
        pendingQuoteMsg = msg;
        String senderName = (msg.sender != null && msg.sender.name != null) ? msg.sender.name : "";
        if ("未知用户".equals(senderName) || "Unknown user".equals(senderName)) {
            senderName = "";
        }
        String text;
        switch (msg.content_type) {
            case 1:
            case 3:
                text = (msg.content != null && !TextUtils.isEmpty(msg.content.text)) ? msg.content.text : "";
                break;
            case 2:
                text = getContext().getString(R.string.preview_image);
                break;
            case 4:
                text = (msg.content != null && !TextUtils.isEmpty(msg.content.file_name)) ? msg.content.file_name : getContext().getString(R.string.preview_file_generic);
                break;
            case 6:
                text = (msg.content != null && !TextUtils.isEmpty(msg.content.post_title)) ? msg.content.post_title : getContext().getString(R.string.preview_article);
                break;
            case 7:
                text = getContext().getString(R.string.preview_sticker);
                break;
            case 10:
                text = getContext().getString(R.string.preview_video);
                break;
            case 11:
                text = getContext().getString(R.string.preview_voice);
                break;
            default:
                if (msg.content != null && msg.content.text != null && !msg.content.text.isEmpty()) {
                    text = msg.content.text;
                } else if (msg.content != null && msg.content.image_url != null && !msg.content.image_url.isEmpty()) {
                    text = getContext().getString(R.string.preview_image);
                } else if (msg.content != null && msg.content.video_url != null && !msg.content.video_url.isEmpty()) {
                    text = getContext().getString(R.string.preview_video);
                } else if (msg.content != null && msg.content.file_name != null && !msg.content.file_name.isEmpty()) {
                    text = msg.content.file_name;
                } else if (msg.content != null && !TextUtils.isEmpty(msg.content.sticker_url)) {
                    text = getContext().getString(R.string.preview_sticker);
                } else {
                    text = getContext().getString(R.string.preview_unknown);
                }
                break;
        }
        if (HtmlNativeRenderer.isHtml(text)) {
            text = android.text.Html.fromHtml(text).toString().trim();
        }
        if (tvQuotePreviewText != null) {
            tvQuotePreviewText.setText(senderName.isEmpty() ? text : senderName + "：" + text);
        }
        if (quotePreviewBar != null) {
            quotePreviewBar.setVisibility(VISIBLE);
        }
    }

    /** Clear the quote preview and dismiss the bar. */
    public void clearQuote() {
        pendingQuoteMsg = null;
        if (quotePreviewBar != null) {
            quotePreviewBar.setVisibility(GONE);
        }
        if (quoteDismissListener != null) {
            quoteDismissListener.onQuoteDismissed();
        }
    }

    /** Returns the pending quote message, or null if none. */
    public Msg getPendingQuoteMsg() {
        return pendingQuoteMsg;
    }

    /**
     * 计算除可展开面板（panelEmoji / panelMore）之外输入栏固有的总高度（含 padding、引用栏、输入控制行）
     */
    public int getNonPanelHeight() {
        int h = getPaddingTop() + getPaddingBottom();
        if (quotePreviewBar != null && quotePreviewBar.getVisibility() == VISIBLE) {
            h += (quotePreviewBar.getHeight() > 0 ? quotePreviewBar.getHeight() : dp(getContext(), 42));
        }
        View inputRow = findViewById(R.id.layoutInputRow);
        if (inputRow != null && inputRow.getHeight() > 0) {
            h += inputRow.getHeight();
        } else {
            h += dp(getContext(), 56);
        }
        return h;
    }

    public void setOnQuoteDismissListener(OnQuoteDismissListener listener) {
        this.quoteDismissListener = listener;
    }

    public void setOnInstructionButtonClickListener(OnInstructionButtonClickListener listener) {
        this.instructionButtonClickListener = listener;
    }

    public void setOnStickerSendListener(OnStickerSendListener listener) {
        this.stickerSendListener = listener;
    }

    public void setInstructionButtonVisibility(int visibility) {
        if (btnInstruction != null) {
            btnInstruction.setVisibility(visibility);
        }
    }

    private void setupPanelActions() {
        View itemActionImage = findViewById(R.id.itemActionImage);
        View itemActionCamera = findViewById(R.id.itemActionCamera);
        View itemActionVideo = findViewById(R.id.itemActionVideo);
        View itemActionRecord = findViewById(R.id.itemActionRecord);
        View itemActionFile = findViewById(R.id.itemActionFile);
        View itemActionCard = findViewById(R.id.itemActionCard);
        View itemActionArticle = findViewById(R.id.itemActionArticle);

        if (itemActionImage != null) {
            itemActionImage.setOnClickListener(v -> handleActionClick("image"));
        }
        if (itemActionCamera != null) {
            itemActionCamera.setOnClickListener(v -> handleActionClick("camera"));
        }
        if (itemActionVideo != null) {
            itemActionVideo.setOnClickListener(v -> handleActionClick("video"));
        }
        if (itemActionRecord != null) {
            itemActionRecord.setOnClickListener(v -> handleActionClick("record"));
        }
        if (itemActionFile != null) {
            itemActionFile.setOnClickListener(v -> handleActionClick("file"));
        }
        if (itemActionCard != null) {
            itemActionCard.setOnClickListener(v -> handleActionClick("card"));
        }
        if (itemActionArticle != null) {
            itemActionArticle.setOnClickListener(v -> handleActionClick("article"));
        }
    }

    private void handleActionClick(String actionType) {
        collapseAllPanels();
        if (panelActionClickListener != null) {
            panelActionClickListener.onActionClick(actionType);
        }
    }

    public void togglePanel() {
        if (isPanelExpanded) {
            collapsePanel();
        } else {
            hideKeyboard();
            collapseEmojiPanel();
            postDelayed(this::expandPanel, 100);
        }
    }

    public void toggleEmojiPanel() {
        if (isEmojiPanelExpanded) {
            collapseEmojiPanel();
            showKeyboard();
        } else {
            hideKeyboard();
            collapsePanel();
            postDelayed(this::expandEmojiPanel, 100);
        }
    }

    public void expandPanel() {
        if (isPanelExpanded) return;
        isPanelExpanded = true;

        if (btnTogglePanel != null) {
            btnTogglePanel.animate().cancel();
            btnTogglePanel.animate()
                    .rotation(45f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }

        if (panelMore != null) {
            if (panelMoreAnimator != null && panelMoreAnimator.isRunning()) {
                panelMoreAnimator.cancel();
            }

            int targetH = cachedPanelMoreHeight;
            if (targetH <= 0) {
                int widthSpec = MeasureSpec.makeMeasureSpec(
                        getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels,
                        MeasureSpec.EXACTLY
                );
                int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                panelMore.measure(widthSpec, heightSpec);
                targetH = panelMore.getMeasuredHeight();
                if (targetH <= 0) {
                    targetH = dp(getContext(), 200);
                }
                cachedPanelMoreHeight = targetH;
            }

            final int finalTargetH = targetH;
            panelMore.setVisibility(VISIBLE);
            panelMore.setAlpha(1f);
            panelMore.setTranslationY(0f);
            ViewGroup.LayoutParams lp = panelMore.getLayoutParams();
            int startH = (lp != null && lp.height > 0 && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) ? lp.height : 0;
            if (lp != null) {
                lp.height = startH;
                panelMore.setLayoutParams(lp);
            }

            panelMoreAnimator = ValueAnimator.ofInt(startH, finalTargetH);
            panelMoreAnimator.setDuration(220);
            panelMoreAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            panelMoreAnimator.addUpdateListener(animation -> {
                int val = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams p = panelMore.getLayoutParams();
                if (p != null) {
                    p.height = val;
                    panelMore.setLayoutParams(p);
                }
            });
            panelMoreAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isPanelExpanded && panelMore != null) {
                        ViewGroup.LayoutParams p = panelMore.getLayoutParams();
                        if (p != null) {
                            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            panelMore.setLayoutParams(p);
                        }
                    }
                }
            });
            panelMoreAnimator.start();
        }
    }

    public void collapsePanel() {
        if (!isPanelExpanded) return;
        isPanelExpanded = false;

        if (btnTogglePanel != null) {
            btnTogglePanel.animate().cancel();
            btnTogglePanel.animate()
                    .rotation(0f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }

        if (panelMore != null) {
            if (panelMoreAnimator != null && panelMoreAnimator.isRunning()) {
                panelMoreAnimator.cancel();
            }

            int curH = panelMore.getHeight();
            if (curH > 0) {
                cachedPanelMoreHeight = curH;
            } else {
                curH = cachedPanelMoreHeight > 0 ? cachedPanelMoreHeight : dp(getContext(), 200);
            }

            panelMoreAnimator = ValueAnimator.ofInt(curH, 0);
            panelMoreAnimator.setDuration(180);
            panelMoreAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            panelMoreAnimator.addUpdateListener(animation -> {
                int val = (int) animation.getAnimatedValue();
                ViewGroup.LayoutParams p = panelMore.getLayoutParams();
                if (p != null) {
                    p.height = val;
                    panelMore.setLayoutParams(p);
                }
            });
            panelMoreAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isPanelExpanded && panelMore != null) {
                        panelMore.setVisibility(GONE);
                        ViewGroup.LayoutParams p = panelMore.getLayoutParams();
                        if (p != null) {
                            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            panelMore.setLayoutParams(p);
                        }
                    }
                }
            });
            panelMoreAnimator.start();
        }
    }

    public void expandEmojiPanel() {
        if (isEmojiPanelExpanded) return;
        isEmojiPanelExpanded = true;

        if (btnEmoji != null) {
            btnEmoji.animate().cancel();
            btnEmoji.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(160)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        if (btnEmoji != null) {
                            btnEmoji.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        }
                    })
                    .start();
        }

        if (panelEmoji != null) {
            if (panelEmojiAnimator != null && panelEmojiAnimator.isRunning()) {
                panelEmojiAnimator.cancel();
            }

            int targetH = panelEmoji.getMinHeightPx();
            panelEmoji.reloadStickers();
            panelEmoji.applyDefaultOrRandomSelection();
            panelEmoji.setVisibility(VISIBLE);
            panelEmoji.setAlpha(1f);
            panelEmoji.setTranslationY(0f);

            int startH = 0;
            ViewGroup.LayoutParams lp = panelEmoji.getLayoutParams();
            if (lp != null && lp.height > 0 && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                startH = lp.height;
            }
            panelEmoji.setPanelHeight(startH);

            panelEmojiAnimator = ValueAnimator.ofInt(startH, targetH);
            panelEmojiAnimator.setDuration(220);
            panelEmojiAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            panelEmojiAnimator.addUpdateListener(animation -> {
                int val = (int) animation.getAnimatedValue();
                if (panelEmoji != null) {
                    panelEmoji.setPanelHeight(val);
                }
            });
            panelEmojiAnimator.start();
        }
    }

    public void collapseEmojiPanel() {
        if (!isEmojiPanelExpanded) return;
        isEmojiPanelExpanded = false;

        if (btnEmoji != null) {
            btnEmoji.animate().cancel();
            btnEmoji.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start();
        }

        if (panelEmoji != null) {
            if (panelEmojiAnimator != null && panelEmojiAnimator.isRunning()) {
                panelEmojiAnimator.cancel();
            }

            int curH = panelEmoji.getHeight();
            if (curH <= 0) {
                curH = panelEmoji.getMinHeightPx();
            }

            panelEmojiAnimator = ValueAnimator.ofInt(curH, 0);
            panelEmojiAnimator.setDuration(180);
            panelEmojiAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
            panelEmojiAnimator.addUpdateListener(animation -> {
                int val = (int) animation.getAnimatedValue();
                if (panelEmoji != null) {
                    panelEmoji.setPanelHeight(val);
                }
            });
            panelEmojiAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isEmojiPanelExpanded && panelEmoji != null) {
                        panelEmoji.setVisibility(GONE);
                        panelEmoji.setPanelHeight(panelEmoji.getMinHeightPx());
                    }
                }
            });
            panelEmojiAnimator.start();
        }
    }

    public void collapseAllPanels() {
        collapsePanel();
        collapseEmojiPanel();
    }

    public void collapseAllPanelsImmediately() {
        isPanelExpanded = false;
        isEmojiPanelExpanded = false;
        if (btnTogglePanel != null) {
            btnTogglePanel.animate().cancel();
            btnTogglePanel.setRotation(0f);
            btnTogglePanel.setScaleX(1.0f);
            btnTogglePanel.setScaleY(1.0f);
        }
        if (btnEmoji != null) {
            btnEmoji.animate().cancel();
            btnEmoji.setScaleX(1.0f);
            btnEmoji.setScaleY(1.0f);
        }
        if (panelMoreAnimator != null && panelMoreAnimator.isRunning()) {
            panelMoreAnimator.cancel();
        }
        if (panelEmojiAnimator != null && panelEmojiAnimator.isRunning()) {
            panelEmojiAnimator.cancel();
        }
        if (panelMore != null) {
            panelMore.setVisibility(GONE);
            ViewGroup.LayoutParams p = panelMore.getLayoutParams();
            if (p != null) {
                p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                panelMore.setLayoutParams(p);
            }
        }
        if (panelEmoji != null) {
            panelEmoji.setVisibility(GONE);
            panelEmoji.setPanelHeight(panelEmoji.getMinHeightPx());
        }
    }

    public boolean isAnyPanelExpanded() {
        return isPanelExpanded || isEmojiPanelExpanded;
    }

    public void reloadStickers() {
        if (panelEmoji != null) {
            panelEmoji.reloadStickers();
        }
    }

    private void hideKeyboard() {
        if (etMessage != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
            }
        }
    }

    private void showKeyboard() {
        if (etMessage != null) {
            etMessage.requestFocus();
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    public boolean isPanelExpanded() {
        return isPanelExpanded;
    }

    public boolean isEmojiPanelExpanded() {
        return isEmojiPanelExpanded;
    }

    public void setOnSendClickListener(OnSendClickListener listener) {
        this.sendClickListener = listener;
    }

    public void setOnPanelActionClickListener(OnPanelActionClickListener listener) {
        this.panelActionClickListener = listener;
    }

    public String getInputText() {
        return (etMessage != null && etMessage.getText() != null) ? etMessage.getText().toString().trim() : "";
    }

    public void clearInput() {
        if (etMessage != null) {
            etMessage.setText("");
        }
    }

    public void setSendEnabled(boolean enabled) {
        if (btnSend != null) {
            boolean hasText = getInputText().length() > 0;
            btnSend.setEnabled(enabled && hasText);
            btnSend.setAlpha((enabled && hasText) ? 1.0f : 0.4f);
        }
    }

    public EditText getEditText() {
        return etMessage;
    }

    public ImageButton getSendButton() {
        return btnSend;
    }

    public ImageButton getTogglePanelButton() {
        return btnTogglePanel;
    }

    public ImageButton getEmojiButton() {
        return btnEmoji;
    }
}
