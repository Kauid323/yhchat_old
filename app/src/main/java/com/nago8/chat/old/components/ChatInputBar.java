package com.nago8.chat.old.components;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nago8.chat.old.R;
import com.nago8.chat.old.proto.Msg;

/**
 * 独立的消息输入框组件（位于 /components/ 文件夹）
 * 支持左侧加号旋转展开/收起下方扩展面板，输入框位于面板上方。
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

    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnTogglePanel;
    private ImageButton btnInstruction;
    private View panelMore;

    private boolean isPanelExpanded = false;

    private OnSendClickListener sendClickListener;
    private OnPanelActionClickListener panelActionClickListener;
    private OnInstructionButtonClickListener instructionButtonClickListener;

    /** Callback invoked when the user dismisses the quote preview bar. */
    public interface OnQuoteDismissListener {
        void onQuoteDismissed();
    }

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
        panelMore = findViewById(R.id.panelMore);

        if (btnTogglePanel != null) {
            btnTogglePanel.setOnClickListener(v -> togglePanel());
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
                    collapsePanel();
                    sendClickListener.onSendClick(text);
                }
            });
            btnSend.setAlpha(0.4f);
            btnSend.setEnabled(false);
        }

        if (etMessage != null) {
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

        setupPanelActions();
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
        if (msg.content != null && msg.content.text != null && !msg.content.text.isEmpty()) {
            text = msg.content.text;
        } else if (msg.content != null && msg.content.image_url != null && !msg.content.image_url.isEmpty()) {
            text = getContext().getString(R.string.preview_image);
        } else if (msg.content != null && msg.content.video_url != null && !msg.content.video_url.isEmpty()) {
            text = getContext().getString(R.string.preview_video);
        } else if (msg.content != null && msg.content.file_name != null && !msg.content.file_name.isEmpty()) {
            text = msg.content.file_name;
        } else {
            text = getContext().getString(R.string.preview_unknown);
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

    public void setOnQuoteDismissListener(OnQuoteDismissListener listener) {
        this.quoteDismissListener = listener;
    }

    public void setOnInstructionButtonClickListener(OnInstructionButtonClickListener listener) {
        this.instructionButtonClickListener = listener;
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
        collapsePanel();
        if (panelActionClickListener != null) {
            panelActionClickListener.onActionClick(actionType);
        }
    }

    public void togglePanel() {
        if (isPanelExpanded) {
            collapsePanel();
        } else {
            expandPanel();
        }
    }

    public void expandPanel() {
        if (isPanelExpanded) return;
        isPanelExpanded = true;
        if (btnTogglePanel != null) {
            btnTogglePanel.animate().rotation(45f).setDuration(200).start();
        }
        if (panelMore != null) {
            panelMore.setVisibility(VISIBLE);
            panelMore.setAlpha(0f);
            panelMore.animate().alpha(1f).setDuration(200).start();
        }
    }

    public void collapsePanel() {
        if (!isPanelExpanded) return;
        isPanelExpanded = false;
        if (btnTogglePanel != null) {
            btnTogglePanel.animate().rotation(0f).setDuration(200).start();
        }
        if (panelMore != null) {
            panelMore.animate().alpha(0f).setDuration(150).withEndAction(() -> panelMore.setVisibility(GONE)).start();
        }
    }

    public boolean isPanelExpanded() {
        return isPanelExpanded;
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
}
