package com.nago8.chat.old.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.PostDetailActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.model.CommunityPostModel;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CommunityPostsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int STATE_LOADING = 0;
    public static final int STATE_CLICK_TO_LOAD = 1;
    public static final int STATE_NO_MORE = 2;

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_FOOTER = 1;
    private static final int VIEW_TYPE_HEADER = 2;

    private final Context context;
    private final List<CommunityPostModel> postList = new ArrayList<>();
    private View headerView;
    private int footerState = STATE_LOADING;
    private OnLoadMoreClickListener onLoadMoreClickListener;
    private OnPostClickListener onPostClickListener;

    public interface OnLoadMoreClickListener {
        void onLoadMoreClick();
    }

    public interface OnPostClickListener {
        void onPostClick(CommunityPostModel post);
    }

    public CommunityPostsAdapter(Context context) {
        this.context = context;
        setHasStableIds(false);
    }

    public void setHeaderView(View headerView) {
        this.headerView = headerView;
        notifyDataSetChanged();
    }

    public void setOnLoadMoreClickListener(OnLoadMoreClickListener listener) {
        this.onLoadMoreClickListener = listener;
    }

    public void setOnPostClickListener(OnPostClickListener listener) {
        this.onPostClickListener = listener;
    }

    public void setPosts(List<CommunityPostModel> newPosts) {
        if (newPosts == null) newPosts = new ArrayList<>();
        this.postList.clear();
        this.postList.addAll(newPosts);
        notifyDataSetChanged();
    }

    public void addPosts(List<CommunityPostModel> newPosts) {
        if (newPosts != null && !newPosts.isEmpty()) {
            int start = (headerView != null ? 1 : 0) + this.postList.size();
            this.postList.addAll(newPosts);
            notifyItemRangeInserted(start, newPosts.size());
        }
    }

    public void setFooterState(int state) {
        if (this.footerState != state) {
            this.footerState = state;
            notifyItemChanged(getItemCount() - 1);
        }
    }

    public int getPostCount() {
        return postList.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (headerView != null && position == 0) {
            return VIEW_TYPE_HEADER;
        }
        int footerPos = (headerView != null ? 1 : 0) + postList.size();
        if (position == footerPos) {
            return VIEW_TYPE_FOOTER;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            return new HeaderViewHolder(headerView);
        }
        if (viewType == VIEW_TYPE_FOOTER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_community_footer, parent, false);
            return new FooterViewHolder(view);
        }
        View view = LayoutInflater.from(context).inflate(R.layout.item_community_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof PostViewHolder) {
            int realPos = position - (headerView != null ? 1 : 0);
            if (realPos >= 0 && realPos < postList.size()) {
                ((PostViewHolder) holder).bind(postList.get(realPos));
            }
        } else if (holder instanceof FooterViewHolder) {
            ((FooterViewHolder) holder).bind(footerState);
        }
    }

    @Override
    public int getItemCount() {
        int headerCount = (headerView != null ? 1 : 0);
        int footerCount = postList.isEmpty() ? 0 : 1;
        return headerCount + postList.size() + footerCount;
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private static class PostDiffCallback extends DiffUtil.Callback {
        private final List<CommunityPostModel> oldList;
        private final List<CommunityPostModel> newList;

        public PostDiffCallback(List<CommunityPostModel> oldList, List<CommunityPostModel> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            CommunityPostModel oldItem = oldList.get(oldItemPosition);
            CommunityPostModel newItem = newList.get(newItemPosition);
            return oldItem.getId() == newItem.getId()
                    && oldItem.getLikeNum() == newItem.getLikeNum()
                    && oldItem.getCommentNum() == newItem.getCommentNum()
                    && oldItem.getCollectNum() == newItem.getCollectNum()
                    && oldItem.isLiked() == newItem.isLiked()
                    && oldItem.isCollected() == newItem.isCollected()
                    && android.text.TextUtils.equals(oldItem.getTitle(), newItem.getTitle())
                    && android.text.TextUtils.equals(oldItem.getContent(), newItem.getContent());
        }
    }

    class FooterViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFooterText;
        private final View progressBar;

        public FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFooterText = itemView.findViewById(R.id.tvFooterText);
            progressBar = itemView.findViewById(R.id.layoutFooterLoading) != null
                    ? ((ViewGroup) itemView.findViewById(R.id.layoutFooterLoading)).getChildAt(0) : null;

            itemView.setOnClickListener(v -> {
                if (footerState == STATE_CLICK_TO_LOAD && onLoadMoreClickListener != null) {
                    onLoadMoreClickListener.onLoadMoreClick();
                }
            });
        }

        public void bind(int state) {
            if (tvFooterText == null) return;
            switch (state) {
                case STATE_LOADING:
                    if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                    tvFooterText.setText(R.string.loading);
                    break;
                case STATE_CLICK_TO_LOAD:
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    tvFooterText.setText(R.string.load_more_click);
                    break;
                case STATE_NO_MORE:
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    tvFooterText.setText(R.string.loaded_all_posts);
                    break;
            }
        }
    }

    class PostViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivPostAuthorAvatar;
        private final TextView tvPostAuthorName;
        private final TextView tvPostTime;
        private final TextView tvPostTitle;
        private final TextView tvPostContent;
        private final ImageView ivLikeIcon;
        private final TextView tvLikeCount;
        private final TextView tvCommentCount;
        private final ImageView ivCollectIcon;
        private final TextView tvCollectCount;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPostAuthorAvatar = itemView.findViewById(R.id.ivPostAuthorAvatar);
            tvPostAuthorName = itemView.findViewById(R.id.tvPostAuthorName);
            tvPostTime = itemView.findViewById(R.id.tvPostTime);
            tvPostTitle = itemView.findViewById(R.id.tvPostTitle);
            tvPostContent = itemView.findViewById(R.id.tvPostContent);
            ivLikeIcon = itemView.findViewById(R.id.ivLikeIcon);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            tvCommentCount = itemView.findViewById(R.id.tvCommentCount);
            ivCollectIcon = itemView.findViewById(R.id.ivCollectIcon);
            tvCollectCount = itemView.findViewById(R.id.tvCollectCount);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                int realPos = pos - (headerView != null ? 1 : 0);
                if (pos != RecyclerView.NO_POSITION && realPos >= 0 && realPos < postList.size()) {
                    CommunityPostModel item = postList.get(realPos);
                    if (onPostClickListener != null) {
                        onPostClickListener.onPostClick(item);
                    } else {
                        Intent intent = new Intent(context, PostDetailActivity.class);
                        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, String.valueOf(item.getId()));
                        intent.putExtra(PostDetailActivity.EXTRA_POST_TITLE, item.getTitle());
                        context.startActivity(intent);
                    }
                }
            });
        }

        public void bind(CommunityPostModel item) {
            if (item == null) return;

            tvPostAuthorName.setText(item.getDisplayAuthorName());
            tvPostTime.setText(item.getCreateTimeText() != null ? item.getCreateTimeText() : "");
            tvPostTitle.setText(item.getTitle() != null ? item.getTitle() : "");
            tvPostContent.setText(item.getContent() != null ? item.getContent() : "");

            tvLikeCount.setText(item.getLikeNumStr());
            tvCommentCount.setText(item.getCommentNumStr());
            tvCollectCount.setText(item.getCollectNumStr());

            ivLikeIcon.setImageResource(item.isLiked() ? R.drawable.ic_like_filled : R.drawable.ic_like_outline);
            ivCollectIcon.setImageResource(item.isCollected() ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);

            String avatarUrl = item.getSenderAvatar();
            Object currentTag = ivPostAuthorAvatar.getTag(R.id.ivPostAuthorAvatar);
            if (!android.text.TextUtils.equals(currentTag != null ? currentTag.toString() : null, avatarUrl)) {
                ivPostAuthorAvatar.setTag(R.id.ivPostAuthorAvatar, avatarUrl);
                ImageUtils.loadAvatar(context, avatarUrl, ivPostAuthorAvatar);
            }
        }
    }
}
