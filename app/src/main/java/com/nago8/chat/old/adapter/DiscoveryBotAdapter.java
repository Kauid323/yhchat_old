package com.nago8.chat.old.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.BotProfileActivity;
import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.model.DiscoveryModels;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscoveryBotAdapter extends RecyclerView.Adapter<DiscoveryBotAdapter.BotViewHolder> {

    private final Context context;
    private final List<DiscoveryModels.BotItem> list = new ArrayList<>();
    private final Set<String> joinedChatIds = new HashSet<>();
    private OnBotActionListener actionListener;

    public interface OnBotActionListener {
        void onApplyBot(DiscoveryModels.BotItem bot);
    }

    private boolean isListMode = false;

    public DiscoveryBotAdapter(Context context) {
        this(context, false);
    }

    public DiscoveryBotAdapter(Context context, boolean isListMode) {
        this.context = context;
        this.isListMode = isListMode;
    }

    public void setListMode(boolean listMode) {
        this.isListMode = listMode;
        notifyDataSetChanged();
    }

    public void setOnBotActionListener(OnBotActionListener listener) {
        this.actionListener = listener;
    }

    public void setJoinedChatIds(Set<String> chatIds) {
        this.joinedChatIds.clear();
        if (chatIds != null) {
            this.joinedChatIds.addAll(chatIds);
        }
        notifyDataSetChanged();
    }

    public void addJoinedChatId(String chatId) {
        if (chatId != null && !chatId.isEmpty()) {
            this.joinedChatIds.add(chatId);
            notifyDataSetChanged();
        }
    }

    public void setData(List<DiscoveryModels.BotItem> data) {
        this.list.clear();
        if (data != null) {
            this.list.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isListMode ? R.layout.item_discovery_bot_list : R.layout.item_discovery_bot;
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new BotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BotViewHolder holder, int position) {
        DiscoveryModels.BotItem item = list.get(position);
        holder.tvBotName.setText(item.getDisplayName());

        String headcount = item.headcount != null && !item.headcount.isEmpty() ? item.headcount : "0";
        holder.tvBotHeadcount.setText("🔥 " + headcount + " 人在用");

        if (item.introduction != null && !item.introduction.trim().isEmpty()) {
            holder.tvBotDesc.setText(item.introduction);
        } else if (item.instructions != null && !item.instructions.trim().isEmpty()) {
            holder.tvBotDesc.setText(item.instructions);
        } else {
            holder.tvBotDesc.setText("暂无功能介绍");
        }

        ImageUtils.loadAvatar(context, item.avatarUrl, holder.ivBotAvatar);

        int themeColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
        float radiusPx = holder.itemView.getResources().getDisplayMetrics().density * 14;
        float strokePx = holder.itemView.getResources().getDisplayMetrics().density * 1;

        boolean isJoined = item.chatId != null && joinedChatIds.contains(item.chatId);
        if (holder.btnBotAction instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) holder.btnBotAction;
            mb.setCornerRadius((int) radiusPx);
            if (isJoined) {
                mb.setText("进入聊天");
                mb.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                mb.setStrokeColor(android.content.res.ColorStateList.valueOf(themeColor));
                mb.setStrokeWidth((int) strokePx);
                mb.setTextColor(themeColor);
            } else {
                mb.setText("添加");
                mb.setBackgroundColor(themeColor);
                mb.setStrokeWidth(0);
                mb.setTextColor(com.nago8.chat.old.utils.ThemeUtils.getContrastingForegroundColor(themeColor));
            }
        } else {
            if (isJoined) {
                holder.btnBotAction.setText("进入聊天");
                holder.btnBotAction.setBackground(com.nago8.chat.old.utils.ThemeUtils.createOutlinedRoundedDrawable(themeColor, radiusPx, strokePx));
                holder.btnBotAction.setTextColor(themeColor);
            } else {
                holder.btnBotAction.setText("添加");
                holder.btnBotAction.setBackground(com.nago8.chat.old.utils.ThemeUtils.createSolidRoundedDrawable(themeColor, radiusPx));
                holder.btnBotAction.setTextColor(com.nago8.chat.old.utils.ThemeUtils.getContrastingForegroundColor(themeColor));
            }
        }

        holder.btnBotAction.setOnClickListener(v -> {
            if (isJoined) {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CHAT_ID, item.chatId);
                intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, 3);
                intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, item.getDisplayName());
                intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, item.avatarUrl);
                context.startActivity(intent);
            } else {
                if (actionListener != null) {
                    actionListener.onApplyBot(item);
                }
            }
        });

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, BotProfileActivity.class);
            intent.putExtra(BotProfileActivity.EXTRA_BOT_ID, item.chatId);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBotAvatar;
        TextView tvBotName;
        TextView tvBotHeadcount;
        TextView tvBotDesc;
        TextView btnBotAction;

        public BotViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBotAvatar = itemView.findViewById(R.id.ivBotAvatar);
            tvBotName = itemView.findViewById(R.id.tvBotName);
            tvBotHeadcount = itemView.findViewById(R.id.tvBotHeadcount);
            tvBotDesc = itemView.findViewById(R.id.tvBotDesc);
            btnBotAction = itemView.findViewById(R.id.btnBotAction);
        }
    }
}
