package com.nago8.chat.old.components;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.nago8.chat.old.R;

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

    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnTogglePanel;
    private View panelMore;

    private boolean isPanelExpanded = false;

    private OnSendClickListener sendClickListener;
    private OnPanelActionClickListener panelActionClickListener;

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

        LayoutInflater.from(context).inflate(R.layout.layout_chat_input_bar, this, true);

        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnTogglePanel = findViewById(R.id.btnTogglePanel);
        panelMore = findViewById(R.id.panelMore);

        if (btnTogglePanel != null) {
            btnTogglePanel.setOnClickListener(v -> togglePanel());
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
