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
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.GroupProfileActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.model.DiscoveryModels;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscoveryGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private final List<DiscoveryModels.GroupItem> list = new ArrayList<>();
    private final Set<String> joinedChatIds = new HashSet<>();
    private OnGroupActionListener actionListener;

    public interface OnGroupActionListener {
        void onApplyGroup(DiscoveryModels.GroupItem group);
    }

    private boolean isWaterfall = true;

    public DiscoveryGroupAdapter(Context context) {
        this(context, true);
    }

    public DiscoveryGroupAdapter(Context context, boolean isWaterfall) {
        this.context = context;
        this.isWaterfall = isWaterfall;
    }

    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_FOOTER = 1;

    private boolean isLoadingMore = false;
    private boolean hasMore = true;

    public void setWaterfall(boolean waterfall) {
        this.isWaterfall = waterfall;
        notifyDataSetChanged();
    }

    public void setOnGroupActionListener(OnGroupActionListener listener) {
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

    public void setData(List<DiscoveryModels.GroupItem> data) {
        this.list.clear();
        if (data != null) {
            this.list.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void addData(List<DiscoveryModels.GroupItem> moreData) {
        if (moreData != null && !moreData.isEmpty()) {
            int start = this.list.size();
            this.list.addAll(moreData);
            notifyItemRangeInserted(start, moreData.size());
        }
    }

    public void setLoadingMore(boolean loadingMore) {
        if (this.isLoadingMore != loadingMore) {
            this.isLoadingMore = loadingMore;
            if (!list.isEmpty()) {
                notifyItemChanged(list.size());
            }
        }
    }

    public void setHasMore(boolean hasMore) {
        if (this.hasMore != hasMore) {
            this.hasMore = hasMore;
            if (!list.isEmpty()) {
                notifyItemChanged(list.size());
            }
        }
    }

    public boolean isLoadingMore() {
        return isLoadingMore;
    }

    public boolean hasMore() {
        return hasMore;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < list.size()) {
            return VIEW_TYPE_ITEM;
        }
        return VIEW_TYPE_FOOTER;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp instanceof StaggeredGridLayoutManager.LayoutParams) {
            StaggeredGridLayoutManager.LayoutParams slp = (StaggeredGridLayoutManager.LayoutParams) lp;
            slp.setFullSpan(holder.getItemViewType() == VIEW_TYPE_FOOTER);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_FOOTER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_discovery_footer, parent, false);
            return new FooterViewHolder(view);
        }
        int layoutId = isWaterfall ? R.layout.item_discovery_group_waterfall : R.layout.item_discovery_group;
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder genericHolder, int position) {
        if (genericHolder instanceof FooterViewHolder) {
            FooterViewHolder fh = (FooterViewHolder) genericHolder;
            if (isLoadingMore) {
                fh.layoutFooterLoading.setVisibility(View.VISIBLE);
                fh.tvFooterNoMore.setVisibility(View.GONE);
            } else if (!hasMore && !list.isEmpty()) {
                fh.layoutFooterLoading.setVisibility(View.GONE);
                fh.tvFooterNoMore.setVisibility(View.VISIBLE);
            } else {
                fh.layoutFooterLoading.setVisibility(View.GONE);
                fh.tvFooterNoMore.setVisibility(View.GONE);
            }
            return;
        }

        GroupViewHolder holder = (GroupViewHolder) genericHolder;
        DiscoveryModels.GroupItem item = list.get(position);
        holder.tvGroupName.setText(item.getDisplayName());

        if (item.category != null && !item.category.trim().isEmpty()) {
            holder.tvGroupCategory.setText(item.category);
            holder.tvGroupCategory.setVisibility(View.VISIBLE);
        } else {
            holder.tvGroupCategory.setVisibility(View.GONE);
        }

        holder.tvGroupHeadcount.setText("👥 " + item.headcount + " 人");

        if (item.introduction != null && !item.introduction.trim().isEmpty()) {
            holder.tvGroupDesc.setText(item.introduction);
            holder.tvGroupDesc.setVisibility(View.VISIBLE);
        } else {
            holder.tvGroupDesc.setText("暂无群简介");
            holder.tvGroupDesc.setVisibility(View.VISIBLE);
        }

        ImageUtils.loadAvatar(context, item.avatarUrl, holder.ivGroupAvatar);

        int themeColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
        float radiusPx = holder.itemView.getResources().getDisplayMetrics().density * 16;
        float strokePx = holder.itemView.getResources().getDisplayMetrics().density * 1;

        boolean isJoined = item.groupId != null && joinedChatIds.contains(item.groupId);
        if (holder.btnGroupAction instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) holder.btnGroupAction;
            mb.setCornerRadius((int) radiusPx);
            if (isJoined) {
                mb.setText("进入聊天");
                mb.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                mb.setStrokeColor(android.content.res.ColorStateList.valueOf(themeColor));
                mb.setStrokeWidth((int) strokePx);
                mb.setTextColor(themeColor);
            } else {
                mb.setText("加入");
                mb.setBackgroundColor(themeColor);
                mb.setStrokeWidth(0);
                mb.setTextColor(com.nago8.chat.old.utils.ThemeUtils.getContrastingForegroundColor(themeColor));
            }
        } else {
            if (isJoined) {
                holder.btnGroupAction.setText("进入聊天");
                holder.btnGroupAction.setBackground(com.nago8.chat.old.utils.ThemeUtils.createOutlinedRoundedDrawable(themeColor, radiusPx, strokePx));
                holder.btnGroupAction.setTextColor(themeColor);
            } else {
                holder.btnGroupAction.setText("加入");
                holder.btnGroupAction.setBackground(com.nago8.chat.old.utils.ThemeUtils.createSolidRoundedDrawable(themeColor, radiusPx));
                holder.btnGroupAction.setTextColor(com.nago8.chat.old.utils.ThemeUtils.getContrastingForegroundColor(themeColor));
            }
        }

        holder.btnGroupAction.setOnClickListener(v -> {
            if (isJoined) {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CHAT_ID, item.groupId);
                intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, 2);
                intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, item.getDisplayName());
                intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, item.avatarUrl);
                context.startActivity(intent);
            } else {
                if (actionListener != null) {
                    actionListener.onApplyGroup(item);
                }
            }
        });

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, GroupProfileActivity.class);
            intent.putExtra(GroupProfileActivity.EXTRA_GROUP_ID, item.groupId);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        if (list.isEmpty()) {
            return 0;
        }
        return list.size() + (isLoadingMore || !hasMore ? 1 : 0);
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        View layoutFooterLoading;
        TextView tvFooterNoMore;

        public FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutFooterLoading = itemView.findViewById(R.id.layoutFooterLoading);
            tvFooterNoMore = itemView.findViewById(R.id.tvFooterNoMore);
        }
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGroupAvatar;
        TextView tvGroupName;
        TextView tvGroupCategory;
        TextView tvGroupHeadcount;
        TextView tvGroupDesc;
        TextView btnGroupAction;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGroupAvatar = itemView.findViewById(R.id.ivGroupAvatar);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvGroupCategory = itemView.findViewById(R.id.tvGroupCategory);
            tvGroupHeadcount = itemView.findViewById(R.id.tvGroupHeadcount);
            tvGroupDesc = itemView.findViewById(R.id.tvGroupDesc);
            btnGroupAction = itemView.findViewById(R.id.btnGroupAction);
        }
    }
}
