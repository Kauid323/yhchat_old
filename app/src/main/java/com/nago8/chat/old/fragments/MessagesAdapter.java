package com.nago8.chat.old.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.ImagePreviewActivity;
import com.nago8.chat.old.PostDetailActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.VideoPlayerActivity;
import com.nago8.chat.old.model.MessageGroup;
import com.nago8.chat.old.net.FileDownloadManager;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.utils.FengEmojiRenderer;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.InternalLinkUtils;
import com.nago8.chat.old.utils.TimeUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {
    private final List<MessageGroup> groups = new ArrayList<>();
    private OnAvatarClickListener avatarClickListener;
    private Markwon markwon;

    // 聊天上下文，供图片预览多图模式使用
    private String chatId;
    private int chatType;
    private String token;

    public void setChatContext(String chatId, int chatType, String token) {
        this.chatId = chatId;
        this.chatType = chatType;
        this.token = token;
    }

    private static final int HEADER_ORDER_LEFT = 1;
    private static final int HEADER_ORDER_RIGHT = 2;

    public interface OnAvatarClickListener {
        void onAvatarClick(String senderId, int senderChatType);
    }

    public interface OnMessageClickListener {
        void onMessageClick(View anchorView, Msg msg, MessageGroup group);
    }

    public interface OnEditHistoryClickListener {
        void onEditHistoryClick(Msg msg);
    }

    private OnMessageClickListener messageClickListener;
    private OnEditHistoryClickListener editHistoryClickListener;

    public void setOnAvatarClickListener(OnAvatarClickListener listener) {
        this.avatarClickListener = listener;
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.messageClickListener = listener;
    }

    public void setOnEditHistoryClickListener(OnEditHistoryClickListener listener) {
        this.editHistoryClickListener = listener;
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

    private Markwon getMarkwon(Context context) {
        int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
        return Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull io.noties.markwon.core.MarkwonTheme.Builder builder) {
                        builder.linkColor(primaryColor);
                    }

                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.linkResolver((view, link) -> {
                            if (!InternalLinkUtils.handleUrl(view.getContext(), link)) {
                                try {
                                    String openUrl = link;
                                    if (!openUrl.startsWith("http://") && !openUrl.startsWith("https://") && !openUrl.startsWith("yunhu://")) {
                                        openUrl = "http://" + openUrl;
                                    }
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(openUrl));
                                    view.getContext().startActivity(intent);
                                } catch (Exception e) {
                                    Log.e("MessagesAdapter", "Failed to resolve link: " + link, e);
                                }
                            }
                        });
                    }
                })
                .build();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MessageGroup group = groups.get(position);
        holder.bind(this, group, avatarClickListener, messageClickListener, editHistoryClickListener, getMarkwon(holder.itemView.getContext()));
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout root;
        LinearLayout groupContainer;
        LinearLayout headerRow;
        LinearLayout messageColumn;
        LinearLayout contentColumn;
        ImageView ivAvatar;
        TextView tvName;
        TextView tvTime;
        TextView tvAdminTag;
        TextView tvOwnerTag;

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
            tvAdminTag = itemView.findViewById(R.id.tvAdminTag);
            tvOwnerTag = itemView.findViewById(R.id.tvOwnerTag);
        }

        void bind(MessagesAdapter adapter, MessageGroup group, OnAvatarClickListener listener, OnMessageClickListener messageClickListener, OnEditHistoryClickListener editHistoryClickListener, Markwon markwon) {
            root.setGravity(group.mine ? Gravity.END : Gravity.START);
            groupContainer.setGravity(group.mine ? Gravity.END : Gravity.START);
            headerRow.setGravity(group.mine ? Gravity.END : Gravity.START);
            contentColumn.setGravity(group.mine ? Gravity.END : Gravity.START);
            messageColumn.setGravity(group.mine ? Gravity.END : Gravity.START);
            applyGroupOrder(group.mine);
            applyHeaderOrder(group.mine);

            String displayName = group.senderName;
            if (displayName == null || "未知用户".equals(displayName) || "Unknown user".equals(displayName)) {
                displayName = "";
            }
            tvName.setText(displayName);
            tvTime.setText(TimeUtils.formatMessageTime(group.firstSendTime));
            ImageUtils.loadAvatar(itemView.getContext(), group.avatarUrl, ivAvatar);
            tvAdminTag.setVisibility(group.isAdmin ? View.VISIBLE : View.GONE);
            tvOwnerTag.setVisibility(group.isOwner ? View.VISIBLE : View.GONE);

            ivAvatar.setOnClickListener(v -> {
                if (listener != null && !TextUtils.isEmpty(group.senderId) && group.senderChatType > 0) {
                    listener.onAvatarClick(group.senderId, group.senderChatType);
                }
            });

            contentColumn.removeAllViews();
            for (int i = 0; i < group.messages.size(); i++) {
                View bubble = createBubble(adapter, group, group.messages.get(i), i, group.messages.size(), markwon, messageClickListener, editHistoryClickListener);
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
                headerRow.addView(tvAdminTag);
                headerRow.addView(tvOwnerTag);
                setHorizontalMargins(tvName, dp(6), dp(4));
                setHorizontalMargins(tvAdminTag, dp(4), dp(4));
                setHorizontalMargins(tvOwnerTag, dp(4), dp(8));
                setHorizontalMargins(tvTime, 0, dp(6));
            } else {
                headerRow.addView(tvOwnerTag);
                headerRow.addView(tvAdminTag);
                headerRow.addView(tvName);
                headerRow.addView(tvTime);
                setHorizontalMargins(tvOwnerTag, 0, dp(4));
                setHorizontalMargins(tvAdminTag, dp(4), dp(4));
                setHorizontalMargins(tvName, dp(4), dp(6));
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

        private View createBubble(MessagesAdapter adapter, MessageGroup group, Msg msg, int index, int count, Markwon markwon, OnMessageClickListener messageClickListener, OnEditHistoryClickListener editHistoryClickListener) {
            View rawBubble = createRawBubble(adapter, group, msg, index, count, markwon);
            View bubbleView = applyRecallIfNeeded(group, msg, rawBubble);

            boolean isRecalled = (msg != null && msg.msg_delete_time > 0);
            boolean isTip = isTipMessage(msg);
            boolean isEdited = (msg != null && msg.edit_time > 0);

            if (!isRecalled && !isTip && messageClickListener != null) {
                bubbleView.setOnLongClickListener(v -> {
                    messageClickListener.onMessageClick(v, msg, group);
                    return true;
                });
            }
            // 点击已编辑消息气泡 -> 弹出编辑历史
            if (isEdited && !isRecalled && editHistoryClickListener != null) {
                bubbleView.setOnClickListener(v -> editHistoryClickListener.onEditHistoryClick(msg));
            }
            return bubbleView;
        }

        private boolean isTipMessage(Msg msg) {
            if (msg == null) return false;
            if (msg.content_type == 9 || msg.content_type == 12) return true;
            if (msg.content != null && !TextUtils.isEmpty(msg.content.tip)
                    && TextUtils.isEmpty(msg.content.text)
                    && TextUtils.isEmpty(msg.content.image_url)
                    && TextUtils.isEmpty(msg.content.video_url)
                    && TextUtils.isEmpty(msg.content.file_name)) {
                return true;
            }
            return false;
        }

        private View createRawBubble(MessagesAdapter adapter, MessageGroup group, Msg msg, int index, int count, Markwon markwon) {
            if (msg != null && msg.msg_delete_time <= 0) {
                if (msg.content_type == 10 || (msg.content != null && msg.content.video_url != null && !msg.content.video_url.isEmpty())) {
                    return createVideoBubble(group, msg, index, count);
                }
                if (msg.content != null && msg.content.image_url != null && !msg.content.image_url.isEmpty()) {
                    return createImageBubble(adapter, group, msg, index, count);
                }
                if (msg.content != null && msg.content.file_name != null && !msg.content.file_name.isEmpty() && msg.content.file_url != null && !msg.content.file_url.isEmpty()) {
                    return createFileBubble(group, msg, index, count);
                }
                if (msg.content_type == 6 || (msg.content != null && msg.content.post_id != null && !msg.content.post_id.isEmpty())) {
                    return createPostBubble(group, msg, index, count);
                }
            }
            boolean isEdited = msg != null && msg.edit_time > 0;
            TextView textView = new TextView(itemView.getContext());
            int emojiSize = dp(22);
            CharSequence displayText = FengEmojiRenderer.apply(itemView.getContext(), getMessageText(msg), emojiSize);
            if (isEdited) {
                textView.setTextColor(0xFFFFFFFF);
                textView.setLinkTextColor(0xFFFFECB3);
            } else {
                int linkColor = group.mine ? 0xFFE0F2FE : 0xFF1A73E8;
                int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(itemView.getContext());
                int fgColor = com.nago8.chat.old.utils.ThemeUtils.getContrastingForegroundColor(primaryColor);
                textView.setTextColor(group.mine ? fgColor : ContextCompat.getColor(itemView.getContext(), R.color.bubble_text_left));
            }

            boolean isMarkdown = msg != null && msg.content_type == 3 && msg.msg_delete_time <= 0;
            if (isMarkdown && markwon != null) {
                markwon.setMarkdown(textView, getMessageText(msg));
            } else {
                textView.setText(displayText, TextView.BufferType.SPANNABLE);
                InternalLinkUtils.processTextViewLinks(textView, group.mine);
            }
            textView.setTextSize(15);
            textView.setGravity(Gravity.START);

            int maxBubbleWidth = getMaxBubbleWidth(itemView.getContext());
            textView.setMaxWidth(maxBubbleWidth);

            String quoteTextStr = (msg != null && msg.msg_delete_time <= 0) ? getQuoteText(msg) : null;
            boolean hasQuote = !TextUtils.isEmpty(quoteTextStr);

            if (hasQuote) {
                Context ctx = itemView.getContext();
                LinearLayout container = new LinearLayout(ctx);
                container.setOrientation(LinearLayout.VERTICAL);
                applyBubbleStyle(container, group.mine, isEdited, index, count);
                container.setPadding(dp(12), dp(8), dp(12), dp(8));

                View quoteView = createQuoteView(ctx, quoteTextStr);
                if (quoteView != null) {
                    container.addView(quoteView);
                }

                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textParams.topMargin = dp(4);
                textView.setLayoutParams(textParams);
                container.addView(textView);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = dp(2);
                params.leftMargin = group.mine ? dp(48) : 0;
                params.rightMargin = group.mine ? 0 : dp(48);
                params.gravity = group.mine ? Gravity.END : Gravity.START;
                container.setLayoutParams(params);
                return container;
            } else {
                applyBubbleStyle(textView, group.mine, isEdited, index, count);
                textView.setPadding(dp(12), dp(8), dp(12), dp(8));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = dp(2);
                params.leftMargin = group.mine ? dp(48) : 0;
                params.rightMargin = group.mine ? 0 : dp(48);
                params.gravity = group.mine ? Gravity.END : Gravity.START;
                textView.setLayoutParams(params);
                return textView;
            }
        }

        private View applyRecallIfNeeded(MessageGroup group, Msg msg, View bubbleView) {
            if (msg == null) {
                return bubbleView;
            }
            if (msg.msg_delete_time > 0 || msg.content_type == 9) {
                bubbleView.setAlpha(0.55f);
            }
            return bubbleView;
        }

        private String formatRecallTime(long deleteTime) {
            long tsMs = deleteTime > 100000000000L ? deleteTime : deleteTime * 1000L;
            Calendar nowCal = Calendar.getInstance();
            Calendar msgCal = Calendar.getInstance();
            msgCal.setTimeInMillis(tsMs);

            String pattern;
            if (msgCal.get(Calendar.YEAR) < nowCal.get(Calendar.YEAR)) {
                pattern = "yyyy年M月d日 HH:mm";
            } else {
                pattern = "M月d日 HH:mm";
            }

            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
            return "该消息已于 " + sdf.format(msgCal.getTime()) + " 撤回";
        }

        private View createVideoBubble(MessageGroup group, Msg msg, int index, int count) {
            final Context ctx = itemView.getContext();
            final String videoUrl = msg.content.video_url;
            final String videoTitle = !TextUtils.isEmpty(msg.content.file_name) ? msg.content.file_name : ctx.getString(R.string.preview_video);

            boolean isEdited = msg != null && msg.edit_time > 0;
            int textColor = isEdited ? 0xFFFFFFFF : ContextCompat.getColor(ctx, group.mine ? android.R.color.white : R.color.bubble_text_left);

            LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.VERTICAL);
            applyBubbleStyle(container, group.mine, isEdited, index, count);
            container.setPadding(dp(12), dp(10), dp(12), dp(10));
            container.setClickable(true);
            container.setFocusable(true);

            String quoteTextStr = getQuoteText(msg);
            if (!TextUtils.isEmpty(quoteTextStr)) {
                View quoteView = createQuoteView(ctx, quoteTextStr);
                if (quoteView != null) {
                    container.addView(quoteView);
                }
            }

            LinearLayout videoRow = new LinearLayout(ctx);
            videoRow.setOrientation(LinearLayout.HORIZONTAL);
            videoRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!TextUtils.isEmpty(quoteTextStr)) {
                rowParams.topMargin = dp(4);
            }
            videoRow.setLayoutParams(rowParams);

            ImageView icon = new ImageView(ctx);
            int iconSize = dp(24);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.rightMargin = dp(8);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(R.drawable.ic_video);
            icon.setColorFilter(textColor);
            videoRow.addView(icon);

            TextView tvVideo = new TextView(ctx);
            tvVideo.setText(videoTitle);
            tvVideo.setTextSize(15);
            tvVideo.setTextColor(textColor);
            tvVideo.setMaxWidth(getMaxBubbleWidth(ctx));
            tvVideo.setSingleLine(true);
            tvVideo.setEllipsize(android.text.TextUtils.TruncateAt.END);
            videoRow.addView(tvVideo);

            container.addView(videoRow);

            container.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, VideoPlayerActivity.class);
                intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, videoUrl);
                intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, videoTitle);
                ctx.startActivity(intent);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            params.leftMargin = group.mine ? dp(48) : 0;
            params.rightMargin = group.mine ? 0 : dp(48);
            params.gravity = group.mine ? Gravity.END : Gravity.START;
            container.setLayoutParams(params);

            return container;
        }

        private View createPostBubble(MessageGroup group, Msg msg, int index, int count) {
            final Context ctx = itemView.getContext();
            final String postId = msg.content.post_id;
            final String postTitle = msg.content.post_title != null ? msg.content.post_title : "";

            boolean isEdited = msg != null && msg.edit_time > 0;
            int textColor = isEdited ? 0xFFFFFFFF : ContextCompat.getColor(ctx, group.mine ? android.R.color.white : R.color.bubble_text_left);

            LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            applyBubbleStyle(container, group.mine, isEdited, index, count);
            container.setPadding(dp(12), dp(10), dp(12), dp(10));
            container.setClickable(true);
            container.setFocusable(true);

            ImageView icon = new ImageView(ctx);
            int iconSize = dp(22);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.rightMargin = dp(8);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(R.drawable.ic_article);
            icon.setColorFilter(textColor);
            container.addView(icon);

            TextView tvTitle = new TextView(ctx);
            tvTitle.setText(!postTitle.isEmpty() ? postTitle : ctx.getString(R.string.preview_article));
            tvTitle.setTextSize(15);
            tvTitle.setTextColor(textColor);
            tvTitle.setMaxWidth(getMaxBubbleWidth(ctx));
            tvTitle.setSingleLine(true);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            container.addView(tvTitle);

            container.setOnClickListener(v -> {
                Intent intent = new Intent(ctx, PostDetailActivity.class);
                intent.putExtra(PostDetailActivity.EXTRA_POST_ID, postId);
                intent.putExtra(PostDetailActivity.EXTRA_POST_TITLE, postTitle);
                ctx.startActivity(intent);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            params.leftMargin = group.mine ? dp(48) : 0;
            params.rightMargin = group.mine ? 0 : dp(48);
            params.gravity = group.mine ? Gravity.END : Gravity.START;
            container.setLayoutParams(params);

            return container;
        }

        private View createFileBubble(MessageGroup group, Msg msg, int index, int count) {
            final Context ctx = itemView.getContext();
            final String fileUrl = msg.content.file_url;
            final String fileName = msg.content.file_name;
            final long fileSize = msg.content.file_size;

            boolean isEdited = msg != null && msg.edit_time > 0;
            final LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.VERTICAL);
            applyBubbleStyle(container, group.mine, isEdited, index, count);
            container.setPadding(dp(12), dp(8), dp(12), dp(8));

            int textColor = isEdited ? 0xFFFFFFFF : ContextCompat.getColor(ctx, group.mine ? android.R.color.white : R.color.bubble_text_left);

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

            final FileDownloadManager dm = FileDownloadManager.getInstance();
            final boolean[] isDownloading = {dm.isDownloading(fileUrl)};

            Runnable updateUI = () -> {
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
            };
            updateUI.run();

            View.OnClickListener clickListener = v -> {
                if (isDownloading[0]) {
                    dm.cancel(fileUrl);
                    isDownloading[0] = false;
                    updateUI.run();
                } else {
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
            };

            View.OnClickListener unifiedClickListener = v -> {
                Object tag = btnIcon.getTag();
                if (tag instanceof File) {
                    FileDownloadManager.openFile(ctx, (File) tag);
                } else {
                    clickListener.onClick(v);
                }
            };
            btnAction.setOnClickListener(unifiedClickListener);
            btnIcon.setOnClickListener(unifiedClickListener);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            params.leftMargin = group.mine ? dp(48) : 0;
            params.rightMargin = group.mine ? 0 : dp(48);
            params.gravity = group.mine ? Gravity.END : Gravity.START;
            container.setLayoutParams(params);
            return container;
        }

        private String getQuoteText(Msg msg) {
            if (msg == null || msg.content == null) return null;
            if (!TextUtils.isEmpty(msg.content.quote_msg_text)) {
                return msg.content.quote_msg_text;
            }
            if (!TextUtils.isEmpty(msg.content.quote_image_url)) {
                if (!TextUtils.isEmpty(msg.content.quote_image_name)) {
                    return "[图片] " + msg.content.quote_image_name;
                }
                return "[图片]";
            }
            if (!TextUtils.isEmpty(msg.content.quote_video_url)) {
                return "[视频]";
            }
            return null;
        }

        private View createQuoteView(Context ctx, String quoteMsgText) {
            if (TextUtils.isEmpty(quoteMsgText) || quoteMsgText.trim().isEmpty()) return null;

            LinearLayout quoteBlock = new LinearLayout(ctx);
            quoteBlock.setOrientation(LinearLayout.HORIZONTAL);
            quoteBlock.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams quoteBlockParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            quoteBlockParams.bottomMargin = dp(4);
            quoteBlock.setLayoutParams(quoteBlockParams);

            View quoteBar = new View(ctx);
            int barW = dp(3);
            int barH = dp(26);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(barW, barH);
            barParams.rightMargin = dp(8);
            quoteBar.setLayoutParams(barParams);
            quoteBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider_color));
            quoteBlock.addView(quoteBar);

            TextView quoteText = new TextView(ctx);
            quoteText.setText(FengEmojiRenderer.apply(ctx, quoteMsgText, dp(18)), TextView.BufferType.SPANNABLE);
            quoteText.setTextSize(13);
            quoteText.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            quoteText.setMaxLines(3);
            quoteText.setMaxWidth(getMaxBubbleWidth(ctx));
            quoteText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            quoteBlock.addView(quoteText);

            return quoteBlock;
        }

        private View createImageBubble(MessagesAdapter adapter, MessageGroup group, Msg msg, int index, int count) {
            Context ctx = itemView.getContext();
            String url = msg != null && msg.content != null ? msg.content.image_url : null;

            boolean isEdited = msg != null && msg.edit_time > 0;
            int textColor = isEdited ? 0xFFFFFFFF : ContextCompat.getColor(ctx, group.mine ? android.R.color.white : R.color.bubble_text_left);

            LinearLayout container = new LinearLayout(ctx);
            container.setOrientation(LinearLayout.VERTICAL);
            applyBubbleStyle(container, group.mine, isEdited, index, count);
            container.setPadding(dp(12), dp(8), dp(12), dp(8));
            container.setClickable(true);

            String quoteTextStr = getQuoteText(msg);
            View quoteView = createQuoteView(ctx, quoteTextStr);
            if (quoteView != null) {
                container.addView(quoteView);
            }

            LinearLayout imageRow = new LinearLayout(ctx);
            imageRow.setOrientation(LinearLayout.HORIZONTAL);
            imageRow.setGravity(Gravity.CENTER_VERTICAL);

            ImageView icon = new ImageView(ctx);
            int iconSize = dp(20);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.rightMargin = dp(6);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(R.drawable.ic_image);
            icon.setColorFilter(textColor);
            imageRow.addView(icon);

            TextView text = new TextView(ctx);
            text.setText(R.string.message_image);
            text.setTextSize(15);
            text.setTextColor(textColor);
            imageRow.addView(text);

            container.addView(imageRow);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            params.leftMargin = group.mine ? dp(48) : 0;
            params.rightMargin = group.mine ? 0 : dp(48);
            params.gravity = group.mine ? Gravity.END : Gravity.START;
            container.setLayoutParams(params);

            container.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(url)) {
                    // 收集所有已加载的图片/表情消息（去重，保留 msg_seq）
                    ArrayList<String> allUrls = new ArrayList<>();
                    ArrayList<Long> allSeqs = new ArrayList<>();
                    int startIdx = 0;
                    if (adapter != null && adapter.groups != null) {
                        for (MessageGroup g : adapter.groups) {
                            if (g.messages == null) continue;
                            for (Msg m : g.messages) {
                                if (m == null || m.content == null) continue;
                                String imgUrl = null;
                                if (!TextUtils.isEmpty(m.content.image_url)) {
                                    imgUrl = m.content.image_url;
                                } else if (!TextUtils.isEmpty(m.content.sticker_url)) {
                                    imgUrl = m.content.sticker_url;
                                }
                                if (imgUrl != null && !allUrls.contains(imgUrl)) {
                                    if (imgUrl.equals(url)) startIdx = allUrls.size();
                                    allUrls.add(imgUrl);
                                    allSeqs.add(m.msg_seq);
                                }
                            }
                        }
                    }
                    if (allUrls.isEmpty()) {
                        allUrls.add(url);
                        allSeqs.add(msg != null ? msg.msg_seq : 0L);
                    }

                    Intent intent = new Intent(ctx, ImagePreviewActivity.class);
                    intent.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, allUrls);
                    intent.putExtra(ImagePreviewActivity.EXTRA_MSG_SEQS, allSeqs);
                    intent.putExtra(ImagePreviewActivity.EXTRA_START_INDEX, startIdx);
                    if (adapter != null) {
                        intent.putExtra(ImagePreviewActivity.EXTRA_CHAT_ID, adapter.chatId);
                        intent.putExtra(ImagePreviewActivity.EXTRA_CHAT_TYPE, adapter.chatType);
                        intent.putExtra(ImagePreviewActivity.EXTRA_TOKEN, adapter.token);
                    }
                    ctx.startActivity(intent);
                }
            });

            return container;
        }

        private void applyBubbleStyle(View bubbleView, boolean mine, boolean isEdited, int index, int count) {
            if (isEdited) {
                android.graphics.drawable.GradientDrawable redBg = new android.graphics.drawable.GradientDrawable();
                redBg.setCornerRadius(dp(16));
                redBg.setColor(0xFFD32F2F);
                bubbleView.setBackground(redBg);
            } else {
                bubbleView.setBackgroundResource(getBubbleBackground(mine, index, count));
                if (mine && bubbleView.getBackground() != null) {
                    int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(bubbleView.getContext());
                    bubbleView.getBackground().mutate().setColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            }
        }

        private int getBubbleBackground(boolean mine, int index, int count) {
            boolean isMiddle = count > 2 && index > 0 && index < count - 1;
            if (isMiddle) {
                return mine ? R.drawable.bg_bubble_right_middle : R.drawable.bg_bubble_left_middle;
            }
            return mine ? R.drawable.bg_bubble_right : R.drawable.bg_bubble_left;
        }

        private String getMessageText(Msg msg) {
            if (msg != null && msg.msg_delete_time > 0) {
                return formatRecallTime(msg.msg_delete_time);
            }
            if (msg == null || msg.content == null) return itemView.getContext().getString(R.string.message_unsupported);
            if (!TextUtils.isEmpty(msg.content.text)) return msg.content.text;
            if (!TextUtils.isEmpty(msg.content.image_url)) return itemView.getContext().getString(R.string.message_image);
            if (!TextUtils.isEmpty(msg.content.file_name)) return itemView.getContext().getString(R.string.message_file, msg.content.file_name);
            if (!TextUtils.isEmpty(msg.content.sticker_url)) return itemView.getContext().getString(R.string.message_sticker);
            if (!TextUtils.isEmpty(msg.content.tip)) return msg.content.tip;
            return itemView.getContext().getString(R.string.message_unsupported);
        }

        private int getMaxBubbleWidth(Context ctx) {
            if (ctx == null) return dp(260);
            int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
            int maxW = (int) (screenWidth * 0.76f);
            int minW = dp(220);
            return Math.max(maxW, minW);
        }

        private int dp(int value) {
            return (int) (value * itemView.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
