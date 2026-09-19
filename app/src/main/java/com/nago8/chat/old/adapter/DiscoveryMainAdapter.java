package com.nago8.chat.old.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.nago8.chat.old.BotDiscoveryActivity;
import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.GroupDiscoveryActivity;
import com.nago8.chat.old.GroupProfileActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.model.DiscoveryModels;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscoveryMainAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_GROUP = 1;
    public static final int VIEW_TYPE_EMPTY = 2;
    public static final int VIEW_TYPE_LOADING = 3;
    public static final int VIEW_TYPE_FOOTER = 4;

    private final Context context;
    private final List<DiscoveryModels.GroupItem> groupList = new ArrayList<>();
    private final Set<String> joinedChatIds = new HashSet<>();

    private boolean isLoadingGroups = false;
    private boolean isLoadingMore = false;
    private boolean hasMore = true;
    private String emptyHint = "暂无推荐群聊";

    // Header Components
    private final DiscoveryBannerAdapter bannerAdapter;
    private final DiscoveryBotAdapter botAdapter;

    private HeaderViewHolder headerViewHolder;
    private OnGroupActionListener groupActionListener;

    public interface OnGroupActionListener {
        void onApplyGroup(DiscoveryModels.GroupItem group);
    }

    public DiscoveryMainAdapter(Context context) {
        this.context = context;
        this.bannerAdapter = new DiscoveryBannerAdapter(context);
        this.botAdapter = new DiscoveryBotAdapter(context);
    }

    public DiscoveryBannerAdapter getBannerAdapter() {
        return bannerAdapter;
    }

    public DiscoveryBotAdapter getBotAdapter() {
        return botAdapter;
    }

    public void setGroupActionListener(OnGroupActionListener listener) {
        this.groupActionListener = listener;
    }

    public void setJoinedChatIds(Set<String> chatIds) {
        this.joinedChatIds.clear();
        if (chatIds != null) {
            this.joinedChatIds.addAll(chatIds);
        }
        this.botAdapter.setJoinedChatIds(this.joinedChatIds);
        notifyDataSetChanged();
    }

    public void addJoinedChatId(String chatId) {
        if (chatId != null && !chatId.isEmpty()) {
            this.joinedChatIds.add(chatId);
            this.botAdapter.addJoinedChatId(chatId);
            notifyDataSetChanged();
        }
    }

    public void setGroupsData(List<DiscoveryModels.GroupItem> groups, boolean loading, String emptyText) {
        this.groupList.clear();
        if (groups != null) {
            this.groupList.addAll(groups);
        }
        this.isLoadingGroups = loading;
        if (emptyText != null) {
            this.emptyHint = emptyText;
        }
        notifyDataSetChanged();
    }

    public void addGroupsData(List<DiscoveryModels.GroupItem> moreGroups, boolean hasMore) {
        if (moreGroups != null && !moreGroups.isEmpty()) {
            int start = 1 + this.groupList.size();
            this.groupList.addAll(moreGroups);
            this.hasMore = hasMore;
            this.isLoadingMore = false;
            notifyItemRangeInserted(start, moreGroups.size());
            notifyItemChanged(1 + this.groupList.size());
        } else {
            this.hasMore = false;
            this.isLoadingMore = false;
            if (!groupList.isEmpty()) {
                notifyItemChanged(1 + this.groupList.size());
            } else {
                notifyDataSetChanged();
            }
        }
    }

    public void setLoadingMore(boolean loadingMore) {
        if (this.isLoadingMore != loadingMore) {
            this.isLoadingMore = loadingMore;
            if (!groupList.isEmpty()) {
                notifyItemChanged(1 + groupList.size());
            }
        }
    }

    public void setHasMore(boolean hasMore) {
        if (this.hasMore != hasMore) {
            this.hasMore = hasMore;
            if (!groupList.isEmpty()) {
                notifyItemChanged(1 + groupList.size());
            }
        }
    }

    public boolean isLoadingMore() {
        return isLoadingMore;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public void setLoading(boolean loading) {
        this.isLoadingGroups = loading;
        notifyDataSetChanged();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp instanceof StaggeredGridLayoutManager.LayoutParams) {
            StaggeredGridLayoutManager.LayoutParams slp = (StaggeredGridLayoutManager.LayoutParams) lp;
            int type = holder.getItemViewType();
            slp.setFullSpan(type == VIEW_TYPE_HEADER || type == VIEW_TYPE_EMPTY || type == VIEW_TYPE_LOADING || type == VIEW_TYPE_FOOTER);
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            GridLayoutManager glm = (GridLayoutManager) layoutManager;
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    int type = getItemViewType(position);
                    if (type == VIEW_TYPE_HEADER || type == VIEW_TYPE_EMPTY || type == VIEW_TYPE_LOADING || type == VIEW_TYPE_FOOTER) {
                        return glm.getSpanCount();
                    }
                    return 1;
                }
            });
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_HEADER;
        }
        if (isLoadingGroups) {
            return VIEW_TYPE_LOADING;
        }
        if (groupList.isEmpty()) {
            return VIEW_TYPE_EMPTY;
        }
        int groupIndex = position - 1;
        if (groupIndex < groupList.size()) {
            return VIEW_TYPE_GROUP;
        }
        return VIEW_TYPE_FOOTER;
    }

    @Override
    public int getItemCount() {
        if (isLoadingGroups || groupList.isEmpty()) {
            return 2; // Header + Loading/Empty
        }
        return 1 + groupList.size() + 1; // Header + Group items + Footer
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case VIEW_TYPE_HEADER: {
                View view = inflater.inflate(R.layout.header_discovery, parent, false);
                HeaderViewHolder holder = new HeaderViewHolder(view);
                holder.setIsRecyclable(false);
                this.headerViewHolder = holder;
                setupHeaderView(holder);
                return holder;
            }
            case VIEW_TYPE_LOADING: {
                View view = inflater.inflate(R.layout.item_discovery_loading, parent, false);
                return new LoadingViewHolder(view);
            }
            case VIEW_TYPE_EMPTY: {
                View view = inflater.inflate(R.layout.item_discovery_empty, parent, false);
                return new EmptyViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = inflater.inflate(R.layout.item_discovery_footer, parent, false);
                return new FooterViewHolder(view);
            }
            case VIEW_TYPE_GROUP:
            default: {
                View view = inflater.inflate(R.layout.item_discovery_group_waterfall, parent, false);
                return new GroupViewHolder(view);
            }
        }
    }

    private void setupHeaderView(HeaderViewHolder holder) {
        holder.vpBanners.setAdapter(bannerAdapter);
        holder.vpBanners.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                holder.updateBannerDots(position);
            }
        });

        holder.rvBots.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        holder.rvBots.setAdapter(botAdapter);

        // 点击「查看全部机器人」
        holder.tvMoreBots.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, BotDiscoveryActivity.class);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        });

        // 点击「查看全部群聊」
        holder.tvMoreGroups.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, GroupDiscoveryActivity.class);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.updateColors();
            h.updateBannerVisibility(bannerAdapter.getItemCount());
            h.updateBotVisibility(botAdapter.getItemCount());
        } else if (holder instanceof EmptyViewHolder) {
            EmptyViewHolder eh = (EmptyViewHolder) holder;
            eh.tvEmptyHint.setText(emptyHint);
        } else if (holder instanceof FooterViewHolder) {
            FooterViewHolder fh = (FooterViewHolder) holder;
            if (isLoadingMore) {
                fh.layoutFooterLoading.setVisibility(View.VISIBLE);
                fh.tvFooterNoMore.setVisibility(View.GONE);
            } else if (!hasMore && !groupList.isEmpty()) {
                fh.layoutFooterLoading.setVisibility(View.GONE);
                fh.tvFooterNoMore.setVisibility(View.VISIBLE);
            } else {
                fh.layoutFooterLoading.setVisibility(View.GONE);
                fh.tvFooterNoMore.setVisibility(View.GONE);
            }
        } else if (holder instanceof GroupViewHolder) {
            int groupIndex = position - 1;
            if (groupIndex >= 0 && groupIndex < groupList.size()) {
                bindGroupItem((GroupViewHolder) holder, groupList.get(groupIndex));
            }
        }
    }

    private void bindGroupItem(GroupViewHolder holder, DiscoveryModels.GroupItem item) {
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
        holder.ivGroupAvatar.setOnClickListener(v -> {
            if (item.avatarUrl != null && !item.avatarUrl.trim().isEmpty()) {
                Intent intent = new Intent(context, com.nago8.chat.old.ImagePreviewActivity.class);
                intent.putExtra(com.nago8.chat.old.ImagePreviewActivity.EXTRA_IMAGE_URL, ImageUtils.appendQiniuParam(item.avatarUrl, 600, 600));
                context.startActivity(intent);
            }
        });

        int themeColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
        float radiusPx = holder.itemView.getResources().getDisplayMetrics().density * 14;
        float strokePx = holder.itemView.getResources().getDisplayMetrics().density * 1;

        boolean isJoined = item.groupId != null && joinedChatIds.contains(item.groupId);
        if (isJoined) {
            holder.btnGroupAction.setText("进入聊天");
            holder.btnGroupAction.setBackground(com.nago8.chat.old.utils.ThemeUtils.createOutlinedRoundedDrawable(themeColor, radiusPx, strokePx));
            holder.btnGroupAction.setTextColor(themeColor);
        } else {
            holder.btnGroupAction.setText("加入");
            holder.btnGroupAction.setBackground(com.nago8.chat.old.utils.ThemeUtils.createSolidRoundedDrawable(themeColor, radiusPx));
            holder.btnGroupAction.setTextColor(com.nago8.chat.old.utils.ThemeUtils.getContrastingForegroundColor(themeColor));
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
                if (groupActionListener != null) {
                    groupActionListener.onApplyGroup(item);
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
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof GroupViewHolder) {
            try {
                com.bumptech.glide.Glide.with(context).clear(((GroupViewHolder) holder).ivGroupAvatar);
            } catch (Exception ignored) {}
        }
    }

    public HeaderViewHolder getHeaderViewHolder() {
        return headerViewHolder;
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public View layoutBannerContainer;
        public ViewPager2 vpBanners;
        public LinearLayout layoutBannerDots;
        public View layoutBotSection;
        public TextView tvMoreBots;
        public RecyclerView rvBots;
        public View layoutGroupSectionHeader;
        public TextView tvMoreGroups;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutBannerContainer = itemView.findViewById(R.id.layoutBannerContainer);
            vpBanners = itemView.findViewById(R.id.vpBanners);
            layoutBannerDots = itemView.findViewById(R.id.layoutBannerDots);
            layoutBotSection = itemView.findViewById(R.id.layoutBotSection);
            tvMoreBots = itemView.findViewById(R.id.tvMoreBots);
            rvBots = itemView.findViewById(R.id.rvBots);
            layoutGroupSectionHeader = itemView.findViewById(R.id.layoutGroupSectionHeader);
            tvMoreGroups = itemView.findViewById(R.id.tvMoreGroups);
        }

        public void updateColors() {
            Context context = itemView.getContext();
            int themeColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
            if (tvMoreBots != null) tvMoreBots.setTextColor(themeColor);
            if (tvMoreGroups != null) tvMoreGroups.setTextColor(themeColor);
        }

        public void updateBannerVisibility(int count) {
            layoutBannerContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            setupBannerDots(count);
        }

        public void updateBotVisibility(int count) {
            layoutBotSection.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        }

        public void setupBannerDots(int count) {
            if (layoutBannerDots.getChildCount() == count) return;
            layoutBannerDots.removeAllViews();
            if (count <= 1) return;

            Context context = itemView.getContext();
            int themeColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
            for (int i = 0; i < count; i++) {
                View dot = new View(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        i == 0 ? dp2px(context, 16) : dp2px(context, 6),
                        dp2px(context, 6)
                );
                params.setMargins(dp2px(context, 3), 0, dp2px(context, 3), 0);
                dot.setLayoutParams(params);
                if (i == 0) {
                    dot.setBackground(com.nago8.chat.old.utils.ThemeUtils.createSolidRoundedDrawable(themeColor, dp2px(context, 3)));
                } else {
                    dot.setBackgroundResource(R.drawable.bg_banner_dot_normal);
                }
                layoutBannerDots.addView(dot);
            }
        }

        public void updateBannerDots(int selectedPosition) {
            int childCount = layoutBannerDots.getChildCount();
            if (childCount <= 1) return;

            Context context = itemView.getContext();
            int themeColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
            for (int i = 0; i < childCount; i++) {
                View dot = layoutBannerDots.getChildAt(i);
                if (dot != null) {
                    boolean isSelected = (i == selectedPosition);
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dot.getLayoutParams();
                    params.width = isSelected ? dp2px(context, 16) : dp2px(context, 6);
                    dot.setLayoutParams(params);
                    if (isSelected) {
                        dot.setBackground(com.nago8.chat.old.utils.ThemeUtils.createSolidRoundedDrawable(themeColor, dp2px(context, 3)));
                    } else {
                        dot.setBackgroundResource(R.drawable.bg_banner_dot_normal);
                    }
                }
            }
        }

        private int dp2px(Context context, int dp) {
            float density = context.getResources().getDisplayMetrics().density;
            return (int) (dp * density + 0.5f);
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

    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmptyHint;

        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmptyHint = itemView.findViewById(R.id.tvEmptyHint);
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
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
}
