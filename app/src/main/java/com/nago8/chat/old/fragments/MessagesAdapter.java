package com.nago8.chat.old.fragments;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.MessageGroup;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {
    private final List<MessageGroup> groups = new ArrayList<>();

    private static final int HEADER_ORDER_LEFT = 1;
    private static final int HEADER_ORDER_RIGHT = 2;

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
        holder.bind(group);
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

        void bind(MessageGroup group) {
            root.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            groupContainer.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            headerRow.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            contentColumn.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            messageColumn.setGravity(group.mine ? Gravity.RIGHT : Gravity.LEFT);
            applyGroupOrder(group.mine);
            applyHeaderOrder(group.mine);

            tvName.setText(group.senderName == null || group.senderName.length() == 0 ? "未知用户" : group.senderName);
            tvTime.setText(TimeUtils.formatMessageTime(group.firstSendTime));
            ImageUtils.loadAvatar(itemView.getContext(), group.avatarUrl, ivAvatar);

            contentColumn.removeAllViews();
            for (int i = 0; i < group.messages.size(); i++) {
                TextView bubble = createBubble(group, group.messages.get(i), i, group.messages.size());
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

        private TextView createBubble(MessageGroup group, Msg msg, int index, int count) {
            TextView textView = new TextView(itemView.getContext());
            textView.setText(getMessageText(msg));
            textView.setTextSize(15);
            textView.setTextColor(itemView.getResources().getColor(group.mine ? android.R.color.white : android.R.color.black));
            textView.setBackgroundResource(getBubbleBackground(group.mine, index, count));
            textView.setPadding(dp(12), dp(8), dp(12), dp(8));
            textView.setGravity(Gravity.LEFT);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            params.leftMargin = group.mine ? dp(48) : 0;
            params.rightMargin = group.mine ? 0 : dp(48);
            params.gravity = group.mine ? Gravity.RIGHT : Gravity.LEFT;
            textView.setLayoutParams(params);
            return textView;
        }

        private int getBubbleBackground(boolean mine, int index, int count) {
            boolean isMiddle = count > 2 && index > 0 && index < count - 1;
            if (isMiddle) {
                return mine ? R.drawable.bg_bubble_right_middle : R.drawable.bg_bubble_left_middle;
            }
            return mine ? R.drawable.bg_bubble_right : R.drawable.bg_bubble_left;
        }

        private String getMessageText(Msg msg) {
            if (msg == null || msg.content == null) return "[不支持的消息]";
            if (msg.content.text != null && msg.content.text.length() > 0) return msg.content.text;
            if (msg.content.image_url != null && msg.content.image_url.length() > 0) return "[图片]";
            if (msg.content.file_name != null && msg.content.file_name.length() > 0) return "[文件] " + msg.content.file_name;
            if (msg.content.sticker_url != null && msg.content.sticker_url.length() > 0) return "[表情]";
            if (msg.content.tip != null && msg.content.tip.length() > 0) return msg.content.tip;
            return "[不支持的消息]";
        }

        private int dp(int value) {
            return (int) (value * itemView.getResources().getDisplayMetrics().density + 0.5f);
        }
    }
}
