package com.nago8.chat.old.fragments;

import android.content.Context;
import android.content.Intent;
import android.text.format.Formatter;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.ImagePreviewActivity;
import com.nago8.chat.old.net.FileDownloadManager;
import com.nago8.chat.old.model.MessageGroup;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.TimeUtils;

import java.io.File;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;

import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {
    private final List<MessageGroup> groups = new ArrayList<>();
    private OnAvatarClickListener avatarClickListener;

    private static final int HEADER_ORDER_LEFT = 1;
    private static final int HEADER_ORDER_RIGHT = 2;

    public interface OnAvatarClickListener {
        void onAvatarClick(String senderId);
    }

    public void setOnAvatarClickListener(OnAvatarClickListener listener) {
        this.avatarClickListener = listener;
    }

    public void setData(List<MessageGroup> data) {
        groups.clear();
        if (data != null) groups.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MessageGroup group = groups.get(position);
        holder.bind(group, avatarClickListener);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout root;
        LinearLayout groupContainer;
        LinearLayout headerRow;
        LinearLayout messageColumn;
        LinearLayout contentColumn;
        ImageView ivAvatar;
        TextView tvName;
        TextView tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.rootMessageGroup);
            groupContainer = itemView.findViewById(R.id.groupContainer);
            headerRow = itemView.findViewById(R.id.headerRow);
            messageColumn = itemView.findViewById(R.id.messageColumn);
            contentColumn = itemView.findViewById(R.id.contentColumn);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        void bind(MessageGroup group, OnAvatarClickListener listener) {
            root.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            groupContainer.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            headerRow.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            contentColumn.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            messageColumn.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            applyGroupOrder(group.mine);
            applyHeaderOrder(group.mine);

            tvName.setText(group.senderName == null || group.senderName.length() == 0 ? itemView.getContext().getString(R.string.unknown_user) : group.senderName);
            tvTime.setText(TimeUtils.formatMessageTime(group.firstSendTime));
            ImageUtils.loadAvatar(itemView.getContext(), group.avatarUrl, ivAvatar);

            ivAvatar.setOnClickListener(v -> {
                if (listener != null && group.senderId != null && group.senderId.length() > 0) {
                    listener.onAvatarClick(group.senderId);
                }
            });

            contentColumn.removeAllViews();
            for (int i = 0; i < group.messages.size(); i++) {
                View bubble = createBubble(group, group.messages.get(i), i, group.messages.size());
                contentColumn.addView(bubble);
            }
        }

        private void applyGroupOrder(boolean mine) {
            int expectedOrder = mine ? HEADER_ORDER_RIGHT : HEADER_ORDER_LEFT;
            Object tag = groupContainer.getTag();
            if (tag instanceof Integer && ((Integer) tag) == expectedOrder) {
                applyMessageColumnMargin(mine);
                return;
            }

            groupContainer.removeAllViews();
            if (mine) {
                groupContainer.addView(messageColumn);
                groupContainer.addView(ivAvatar);
            } else {
                groupContainer.addView(ivAvatar);
                groupContainer.addView(messageColumn);
            }
            groupContainer.setTag(expectedOrder);
            applyMessageColumnMargin(mine);
        }

        private void applyMessageColumnMargin(boolean mine) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageColumn.getLayoutParams();
            params.leftMargin = mine ? 0 : dp(8);
            params.rightMargin = mine ? dp(8) : 0;
            messageColumn.setLayoutParams(params);
        }

        private void applyHeaderOrder(boolean mine) {
            int expectedOrder = mine ? HEADER_ORDER_RIGHT : HEADER_ORDER_LEFT;
            Object tag = headerRow.getTag();
            if (tag instanceof Integer && ((Integer) tag) == expectedOrder) return;

            headerRow.removeAllViews();
            if (mine) {
                headerRow.addView(tvTime);
                headerRow.addView(tvName);
                setHorizontalMargins(tvName, dp(6), dp(8));
                setHorizontalMargins(tvTime, 0, dp(6));
            } else {
                headerRow.addView(tvName);
                headerRow.addView(tvTime);
                setHorizontalMargins(tvName, 0, dp(6));
                setHorizontalMargins(tvTime, 0, 0);
            }
            headerRow.setTag(expectedOrder);
        }

        private void setHorizontalMargins(View view, int left, int right) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
            params.leftMargin = left;
            params.rightMargin = right;
            view.setLayoutParams(params);
        }

        private View createBubble(MessageGroup group, Msg msg, int index, int count) {
            if (msg != null && msg.content != null && msg.content.image_url != null && msg.content.image_url.length() > 0) {
                return createImageBubble(group, msg, index, count);
            }
            if (msg != null && msg.content != null && msg.content.file_name != null && msg.content.file_name.length() > 0 && msg.content.file_url != null && msg.content.file_url.length() > 0) {
                return createFileBubble(group, msg, index, count);
            }
            TextView textView = new TextView(itemView.getContext());
            // content_type=3 为 markdown 消息，用 Markwon 渲染
            if (msg != null && msg.content_type == 3 && msg.content != null && msg.content.text != null && msg.content.text.length() > 0) {
                Markwon markwon = Markwon.builder(itemView.getContext())
                        .usePlugin(StrikethroughPlugin.create())
                        .build();
                markwon.setMarkdown(textView, msg.content.text);
            } else {
                textView.setText(getMessageText(msg));
            }
            textView.setTextSize(15);
            textView.setTextColor(itemView.getResources().getColor(group.mine ? android.R.color.white : R.color.bubble_text_left));
            textView.setGravity(Gravity.LEFT);

            // 判断是否有引用消息
            boolean hasQuote = msg != null && msg.content != null
                    && msg.content.quote_msg_text != null && msg.content.quote_msg_text.length() > 0;

            if (hasQuote) {
                // 带引用的消息：垂直布局，引用块在上，消息文本在下
                Context ctx = itemView.getContext();
                LinearLayout container = new LinearLayout(ctx);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setBackgroundResource(getBubbleBackground(group.mine, index, count));
                container.setPadding(dp(12), dp(8), dp(12), dp(8));

                // 引用块：水平布局，左侧竖线 + 引用文本
                LinearLayout quoteBlock = new LinearLayout(ctx);
                quoteBlock.setOrientation(LinearLayout.HORIZONTAL);
                quoteBlock.setGravity(Gravity.CENTER_VERTICAL);

                View quoteBar = new View(ctx);
                int barW = dp(3);
                int barH = dp(28);
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(barW, barH);
                barParams.rightMargin = dp(8);
                quoteBar.setLayoutParams(barParams);
                quoteBar.setBackgroundColor(0x66888888);
                quoteBlock.addView(quoteBar);

                TextView quoteText = new TextView(ctx);
                quoteText.setText(msg.content.quote_msg_text);
                quoteText.setTextSize(13);
                quoteText.setTextColor(0x99888888);
                quoteText.setMaxLines(3);
                quoteText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                quoteBlock.addView(quoteText);

                container.addView(quoteBlock);

                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textParams.topMargin = dp(4);
                textView.setLayoutParams(textParams);
                container.addView(textView);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = dp(2);
                params.leftMargin = group.mine ? dp(48) : 0;
                params.rightMargin = group.mine ? 0 : dp(48);
                params.gravity = group.mine ? Gravity.RIGHT : Gravity.LEFT;
                container.setLayoutParams(params);
                return container;
            } else {
                // 普通文本消息
                textView.setBackgroundResource(getBubbleBackground(group.mine, index, count));
                textView.setPadding(dp(12), dp(8), dp(12), dp(8));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = dp(2);
                params.leftMargin = group.mine ? dp(48) : 0;
                params.rightMargin = group.mine ? 0 : dp(48);
                params.gravity = group.mine ? Gravity.RIGHT : Gravity.LEFT;
                textView.setLayoutParams(params);
                return textView;
            }
        }

        private View createFileBubble(MessageGroup group, Msg msg, int index, int count) {
            final Context ctx = itemView.getContext();
            final String fileUrl = msg.content.file_url;
            final String fileName = msg.content.file_name;
            final long fileSize = msg.content.file_size;

            final LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackgroundResource(getBubbleBackground(group.mine, index, count));
            container.setPadding(dp(12), dp(8), dp(12), dp(8));

            int textColor = itemView.getResources().getColor(group.mine ? android.R.color.white : R.color.bubble_text_left);

            // 文件信息行：图标 + 文件名 + 大小
            LinearLayout infoRow = new LinearLayout(ctx);
            infoRow.setOrientation(LinearLayout.HORIZONTAL);
            infoRow.setGravity(Gravity.CENTER_VERTICAL);

            ImageView icon = new ImageView(ctx);
            int iconSize = dp(20);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.rightMargin = dp(8);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(R.drawable.ic_file);
            icon.setColorFilter(textColor);
            infoRow.addView(icon);

            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView tvName = new TextView(ctx);
            tvName.setText(fileName);
            tvName.setTextSize(15);
            tvName.setTextColor(textColor);
            tvName.setMaxWidth(dp(200));
            tvName.setSingleLine(true);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            textCol.addView(tvName);

            TextView tvSize = new TextView(ctx);
            tvSize.setTextSize(12);
            tvSize.setTextColor(textColor);
            tvSize.setAlpha(0.7f);
            if (fileSize > 0) {
                tvSize.setText(Formatter.formatFileSize(ctx, fileSize));
            } else {
                tvSize.setText("");
            }
            textCol.addView(tvSize);

            infoRow.addView(textCol);
            container.addView(infoRow);

            // 操作行：下载按钮 / 进度条+取消按钮 / 打开按钮
            final LinearLayout actionRow = new LinearLayout(ctx);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setGravity(Gravity.CENTER_VERTICAL);
            actionRow.setPadding(0, dp(6), 0, 0);

            final TextView btnAction = new TextView(ctx);
            btnAction.setTextSize(13);
            btnAction.setTextColor(textColor);
            btnAction.setPadding(dp(8), dp(4), dp(8), dp(4));
            btnAction.setBackgroundResource(0);

            final ProgressBar progressBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
            LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(0, dp(16));
            pbParams.weight = 1;
            pbParams.rightMargin = dp(8);
            progressBar.setLayoutParams(pbParams);
            progressBar.setMax(100);
            progressBar.setVisibility(View.GONE);

            final ImageView btnIcon = new ImageView(ctx);
            int btnIconSize = dp(24);
            LinearLayout.LayoutParams btnIconParams = new LinearLayout.LayoutParams(btnIconSize, btnIconSize);
            btnIcon.setLayoutParams(btnIconParams);
            btnIcon.setColorFilter(textColor);

            actionRow.addView(progressBar);
            actionRow.addView(btnAction);
            actionRow.addView(btnIcon);
            container.addView(actionRow);

            // 状态管理
            final FileDownloadManager dm = FileDownloadManager.getInstance();
            final boolean[] isDownloading = {dm.isDownloading(fileUrl)};

            Runnable updateUI = new Runnable() {
                @Override
                public void run() {
                    if (isDownloading[0]) {
                        btnAction.setVisibility(View.GONE);
                        progressBar.setVisibility(View.VISIBLE);
                        btnIcon.setVisibility(View.VISIBLE);
                        btnIcon.setImageResource(R.drawable.ic_close);
                        int p = dm.getProgress(fileUrl);
                        if (p >= 0) progressBar.setProgress(p);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnAction.setVisibility(View.VISIBLE);
                        btnIcon.setVisibility(View.VISIBLE);
                        btnAction.setText(R.string.message_file_download);
                        btnIcon.setImageResource(R.drawable.ic_download);
                    }
                }
            };
            updateUI.run();

            View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isDownloading[0]) {
                        // 取消下载
                        dm.cancel(fileUrl);
                        isDownloading[0] = false;
                        updateUI.run();
                    } else {
                        // 开始下载
                        isDownloading[0] = true;
                        updateUI.run();
                        dm.download(ctx, fileUrl, fileName, new FileDownloadManager.DownloadCallback() {
                            @Override
                            public void onProgress(int percent) {
                                ((android.app.Activity) ctx).runOnUiThread(() -> {
                                    progressBar.setProgress(percent);
                                    btnAction.setVisibility(View.VISIBLE);
                                    btnAction.setText(ctx.getString(R.string.message_file_downloading, percent));
                                    btnAction.setAlpha(0.7f);
                                });
                            }

                            @Override
                            public void onComplete(File file) {
                                ((android.app.Activity) ctx).runOnUiThread(() -> {
                                    isDownloading[0] = false;
                                    progressBar.setVisibility(View.GONE);
                                    btnAction.setText(R.string.message_file_open);
                                    btnAction.setAlpha(1f);
                                    btnIcon.setImageResource(R.drawable.ic_file);
                                    btnIcon.setTag(file);
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                ((android.app.Activity) ctx).runOnUiThread(() -> {
                                    isDownloading[0] = false;
                                    updateUI.run();
                                    btnAction.setText(R.string.message_file_failed);
                                });
                            }

                            @Override
                            public void onCancel() {
                                ((android.app.Activity) ctx).runOnUiThread(() -> {
                                    isDownloading[0] = false;
                                    updateUI.run();
                                });
                            }
                        });
                    }
                }
            };

            // 统一点击逻辑：下载中→取消，已下载→打开，未下载→开始下载
            View.OnClickListener unifiedClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                Object tag = btnIcon.getTag();
                if (tag instanceof File) {
                    FileDownloadManager.openFile(ctx, (File) tag);
                } else {
                    clickListener.onClick(v);
                }
                }
            };
            btnAction.setOnClickListener(unifiedClickListener);
            btnIcon.setOnClickListener(unifiedClickListener);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            params.leftMargin = group.mine ? dp(48) : 0;
            params.rightMargin = group.mine ? 0 : dp(48);
            params.gravity = group.mine ? Gravity.RIGHT : Gravity.LEFT;
            container.setLayoutParams(params);
            return container;
        }

        private View createImageBubble(MessageGroup group, Msg msg, int index, int count) {
            Context ctx = itemView.getContext();
            String url = msg.content.image_url;

            LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setBackgroundResource(getBubbleBackground(group.mine, index, count));
            container.setPadding(dp(12), dp(8), dp(12), dp(8));
            container.setClickable(true);

            ImageView icon = new ImageView(ctx);
            int iconSize = dp(20);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.rightMargin = dp(6);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(R.drawable.ic_image);
            icon.setColorFilter(itemView.getResources().getColor(group.mine ? android.R.color.white : R.color.bubble_text_left));
            container.addView(icon);

            TextView text = new TextView(ctx);
            text.setText(R.string.message_image);
            text.setTextSize(15);
            text.setTextColor(itemView.getResources().getColor(group.mine ? android.R.color.white : R.color.bubble_text_left));
            container.addView(text);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            params.leftMargin = group.mine ? dp(48) : 0;
            params.rightMargin = group.mine ? 0 : dp(48);
            params.gravity = group.mine ? Gravity.RIGHT : Gravity.LEFT;
            container.setLayoutParams(params);

            container.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, ImagePreviewActivity.class);
                intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, url);
                ctx.startActivity(intent);
            });

            return container;
        }

        private int getBubbleBackground(boolean mine, int index, int count) {
            boolean isMiddle = count > 2 && index > 0 && index < count - 1;
            if (isMiddle) {
                return mine ? R.drawable.bg_bubble_right_middle : R.drawable.bg_bubble_left_middle;
            }
            return mine ? R.drawable.bg_bubble_right : R.drawable.bg_bubble_left;
        }

        private String getMessageText(Msg msg) {
            if (msg == null || msg.content == null) return itemView.getContext().getString(R.string.message_unsupported);
            if (msg.content.text != null && msg.content.text.length() > 0) return msg.content.text;
            if (msg.content.image_url != null && msg.content.image_url.length() > 0) return itemView.getContext().getString(R.string.message_image);
            if (msg.content.file_name != null && msg.content.file_name.length() > 0) return itemView.getContext().getString(R.string.message_file, msg.content.file_name);
            if (msg.content.sticker_url != null && msg.content.sticker_url.length() > 0) return itemView.getContext().getString(R.string.message_sticker);
            if (msg.content.tip != null && msg.content.tip.length() > 0) return msg.content.tip;
            return itemView.getContext().getString(R.string.message_unsupported);
        }

        private int dp(int value) {
            return (int) (value * itemView.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
