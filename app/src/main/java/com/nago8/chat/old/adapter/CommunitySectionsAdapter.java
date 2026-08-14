package com.nago8.chat.old.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.CommunityBaModel;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CommunitySectionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int STATE_LOADING = 0;
    public static final int STATE_CLICK_TO_LOAD = 1;
    public static final int STATE_NO_MORE = 2;

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    private final Context context;
    private final List<CommunityBaModel> sectionList = new ArrayList<>();
    private int footerState = STATE_LOADING;
    private OnLoadMoreClickListener onLoadMoreClickListener;
    private OnSectionClickListener onSectionClickListener;

    public interface OnLoadMoreClickListener {
        void onLoadMoreClick();
    }

    public interface OnSectionClickListener {
        void onSectionClick(CommunityBaModel item);
    }

    public CommunitySectionsAdapter(Context context) {
        this.context = context;
    }

    public void setOnLoadMoreClickListener(OnLoadMoreClickListener listener) {
        this.onLoadMoreClickListener = listener;
    }

    public void setOnSectionClickListener(OnSectionClickListener listener) {
        this.onSectionClickListener = listener;
    }

    public void setSections(List<CommunityBaModel> newSections) {
        if (newSections == null) newSections = new ArrayList<>();

        final List<CommunityBaModel> oldList = new ArrayList<>(this.sectionList);
        final List<CommunityBaModel> newList = new ArrayList<>(newSections);

        boolean oldEmpty = oldList.isEmpty();
        boolean newEmpty = newList.isEmpty();

        this.sectionList.clear();
        this.sectionList.addAll(newList);

        if (oldEmpty || newEmpty) {
            notifyDataSetChanged();
        } else {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new BaDiffCallback(oldList, newList));
            diffResult.dispatchUpdatesTo(this);
        }
    }

    public void addSections(List<CommunityBaModel> newSections) {
        if (newSections != null && !newSections.isEmpty()) {
            int start = this.sectionList.size();
            this.sectionList.addAll(newSections);
            notifyItemRangeInserted(start, newSections.size());
        }
    }

    public void setFooterState(int state) {
        if (this.footerState != state) {
            this.footerState = state;
            notifyItemChanged(getItemCount() - 1);
        }
    }

    public int getSectionCount() {
        return sectionList.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == sectionList.size()) {
            return VIEW_TYPE_FOOTER;
        }
        return VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_FOOTER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_community_footer, parent, false);
            return new FooterViewHolder(view);
        }
        View view = LayoutInflater.from(context).inflate(R.layout.item_community_section, parent, false);
        return new SectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).bind(sectionList.get(position));
        } else if (holder instanceof FooterViewHolder) {
            ((FooterViewHolder) holder).bind(footerState);
        }
    }

    @Override
    public int getItemCount() {
        return sectionList.isEmpty() ? 0 : sectionList.size() + 1;
    }

    private static class BaDiffCallback extends DiffUtil.Callback {
        private final List<CommunityBaModel> oldList;
        private final List<CommunityBaModel> newList;

        public BaDiffCallback(List<CommunityBaModel> oldList, List<CommunityBaModel> newList) {
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
            CommunityBaModel oldItem = oldList.get(oldItemPosition);
            CommunityBaModel newItem = newList.get(newItemPosition);
            return oldItem.getId() == newItem.getId()
                    && oldItem.getMemberNum() == newItem.getMemberNum()
                    && oldItem.getPostNum() == newItem.getPostNum()
                    && oldItem.getGroupNum() == newItem.getGroupNum()
                    && android.text.TextUtils.equals(oldItem.getName(), newItem.getName())
                    && android.text.TextUtils.equals(oldItem.getAvatar(), newItem.getAvatar());
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
                    tvFooterText.setText(R.string.loaded_all_sections);
                    break;
            }
        }
    }

    class SectionViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivBaAvatar;
        private final TextView tvBaName;
        private final TextView tvBaStats;

        public SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBaAvatar = itemView.findViewById(R.id.ivBaAvatar);
            tvBaName = itemView.findViewById(R.id.tvBaName);
            tvBaStats = itemView.findViewById(R.id.tvBaStats);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < sectionList.size()) {
                    CommunityBaModel item = sectionList.get(pos);
                    if (item != null) {
                        if (onSectionClickListener != null) {
                            onSectionClickListener.onSectionClick(item);
                        } else {
                            android.content.Intent intent = new android.content.Intent(context, com.nago8.chat.old.SectionDetailActivity.class);
                            intent.putExtra(com.nago8.chat.old.SectionDetailActivity.EXTRA_BA_ID, item.getId());
                            intent.putExtra(com.nago8.chat.old.SectionDetailActivity.EXTRA_BA_NAME, item.getName());
                            context.startActivity(intent);
                        }
                    }
                }
            });
        }

        public void bind(CommunityBaModel item) {
            if (item == null) return;
            tvBaName.setText(item.getName() != null ? item.getName() : "");
            tvBaStats.setText(item.getStatsText(context));

            String avatarUrl = item.getAvatar();
            Object currentTag = ivBaAvatar.getTag(R.id.ivBaAvatar);
            if (!android.text.TextUtils.equals(currentTag != null ? currentTag.toString() : null, avatarUrl)) {
                ivBaAvatar.setTag(R.id.ivBaAvatar, avatarUrl);
                ImageUtils.loadAvatar(context, avatarUrl, ivBaAvatar);
            }
        }
    }
}
