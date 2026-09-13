package com.nago8.chat.old.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.nago8.chat.old.PostDetailActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.SectionDetailActivity;
import com.nago8.chat.old.model.CommunityBaModel;
import com.nago8.chat.old.model.CommunityPostModel;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;

public class CommunitySearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_BOARD = 1;
    public static final int VIEW_TYPE_POST = 2;

    private final Context context;
    private final List<Object> items = new ArrayList<>();

    public interface OnItemClickListener {
        void onBoardClick(CommunityBaModel board);
        void onPostClick(CommunityPostModel post);
    }

    private OnItemClickListener listener;

    public CommunitySearchAdapter(Context context) {
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<CommunityBaModel> boards, List<CommunityPostModel> posts) {
        this.items.clear();
        if (boards != null && !boards.isEmpty()) {
            items.add("相关分区 (" + boards.size() + ")");
            items.addAll(boards);
        }
        if (posts != null && !posts.isEmpty()) {
            items.add("相关文章 (" + posts.size() + ")");
            items.addAll(posts);
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof String) {
            return VIEW_TYPE_HEADER;
        } else if (item instanceof CommunityBaModel) {
            return VIEW_TYPE_BOARD;
        } else {
            return VIEW_TYPE_POST;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_community_search_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == VIEW_TYPE_BOARD) {
            View view = inflater.inflate(R.layout.item_community_search_board, parent, false);
            return new BoardViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_community_post, parent, false);
            return new PostViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int themeColor = ThemeUtils.getThemeColor(context);
        Object item = items.get(position);

        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder hh = (HeaderViewHolder) holder;
            hh.tvHeader.setText((String) item);
            hh.tvHeader.setTextColor(themeColor);
        } else if (holder instanceof BoardViewHolder) {
            BoardViewHolder bh = (BoardViewHolder) holder;
            CommunityBaModel board = (CommunityBaModel) item;
            bh.tvBoardName.setText(board.getName());
            bh.tvBoardStats.setText(board.getStatsText(context));
            ImageUtils.loadAvatar(context, board.getAvatar(), bh.ivBoardAvatar);

            if (bh.btnEnterBoard instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) bh.btnEnterBoard;
                mb.setTextColor(themeColor);
                mb.setStrokeColor(android.content.res.ColorStateList.valueOf(themeColor));
                mb.setStrokeWidth((int) (context.getResources().getDisplayMetrics().density * 1));
                mb.setCornerRadius((int) (context.getResources().getDisplayMetrics().density * 16));
            } else {
                bh.btnEnterBoard.setTextColor(themeColor);
                float radius = context.getResources().getDisplayMetrics().density * 14;
                float stroke = context.getResources().getDisplayMetrics().density * 1;
                bh.btnEnterBoard.setBackground(ThemeUtils.createOutlinedRoundedDrawable(themeColor, radius, stroke));
            }

            View.OnClickListener clickListener = v -> {
                if (listener != null) {
                    listener.onBoardClick(board);
                } else {
                    Intent intent = new Intent(context, SectionDetailActivity.class);
                    intent.putExtra(SectionDetailActivity.EXTRA_BA_ID, board.getId());
                    intent.putExtra(SectionDetailActivity.EXTRA_BA_NAME, board.getName());
                    context.startActivity(intent);
                }
            };
            bh.itemView.setOnClickListener(clickListener);
            bh.btnEnterBoard.setOnClickListener(clickListener);
        } else if (holder instanceof PostViewHolder) {
            PostViewHolder ph = (PostViewHolder) holder;
            CommunityPostModel post = (CommunityPostModel) item;

            ph.tvAuthorName.setText(post.getDisplayAuthorName());
            ph.tvTime.setText(post.getCreateTimeText());
            ph.tvTitle.setText(post.getTitle());
            ph.tvContent.setText(post.getContent());
            ph.tvLikeCount.setText(post.getLikeNumStr());
            ph.tvCommentCount.setText(post.getCommentNumStr());
            ph.tvCollectCount.setText(post.getCollectNumStr());

            ImageUtils.loadAvatar(context, post.getSenderAvatar(), ph.ivAuthorAvatar);

            ph.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPostClick(post);
                } else {
                    Intent intent = new Intent(context, PostDetailActivity.class);
                    intent.putExtra(PostDetailActivity.EXTRA_POST_ID, String.valueOf(post.getId()));
                    intent.putExtra(PostDetailActivity.EXTRA_POST_TITLE, post.getTitle());
                    intent.putExtra(PostDetailActivity.EXTRA_BA_ID, post.getBaId());
                    context.startActivity(intent);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(View view) {
            super(view);
            tvHeader = view.findViewById(R.id.tvSectionHeader);
        }
    }

    static class BoardViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivBoardAvatar;
        TextView tvBoardName;
        TextView tvBoardStats;
        TextView btnEnterBoard;

        BoardViewHolder(View view) {
            super(view);
            ivBoardAvatar = view.findViewById(R.id.ivBoardAvatar);
            tvBoardName = view.findViewById(R.id.tvBoardName);
            tvBoardStats = view.findViewById(R.id.tvBoardStats);
            btnEnterBoard = view.findViewById(R.id.btnEnterBoard);
        }
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivAuthorAvatar;
        TextView tvAuthorName;
        TextView tvTime;
        TextView tvTitle;
        TextView tvContent;
        TextView tvLikeCount;
        TextView tvCommentCount;
        TextView tvCollectCount;

        PostViewHolder(View view) {
            super(view);
            ivAuthorAvatar = view.findViewById(R.id.ivPostAuthorAvatar);
            tvAuthorName = view.findViewById(R.id.tvPostAuthorName);
            tvTime = view.findViewById(R.id.tvPostTime);
            tvTitle = view.findViewById(R.id.tvPostTitle);
            tvContent = view.findViewById(R.id.tvPostContent);
            tvLikeCount = view.findViewById(R.id.tvLikeCount);
            tvCommentCount = view.findViewById(R.id.tvCommentCount);
            tvCollectCount = view.findViewById(R.id.tvCollectCount);
        }
    }
}
