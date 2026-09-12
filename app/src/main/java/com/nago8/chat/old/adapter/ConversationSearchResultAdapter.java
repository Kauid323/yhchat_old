package com.nago8.chat.old.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.nago8.chat.old.R;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class ConversationSearchResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_ITEM = 1;

    public static class Item {
        public final boolean isHeader;
        public final String headerTitle;
        public final String chatId;
        public final int chatType;
        public final String name;
        public final String avatarUrl;
        public final String subtitle;

        public static Item header(String title) {
            return new Item(true, title, null, 1, null, null, null);
        }

        public static Item result(String chatId, int chatType, String name, String avatarUrl, String subtitle) {
            return new Item(false, null, chatId, chatType, name, avatarUrl, subtitle);
        }

        private Item(boolean isHeader, String headerTitle, String chatId, int chatType, String name, String avatarUrl, String subtitle) {
            this.isHeader = isHeader;
            this.headerTitle = headerTitle;
            this.chatId = chatId;
            this.chatType = chatType;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.subtitle = subtitle;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Item item);
    }

    private final Context context;
    private final List<Item> items = new ArrayList<>();
    private OnItemClickListener listener;

    public ConversationSearchResultAdapter(Context context) {
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<Item> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void clear() {
        this.items.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_floating_search_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_floating_search_result, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvTitle.setText(item.headerTitle);
        } else if (holder instanceof ItemViewHolder) {
            ItemViewHolder ivh = (ItemViewHolder) holder;
            ivh.tvName.setText(item.name != null ? item.name : "");

            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                ivh.tvSub.setVisibility(View.VISIBLE);
                ivh.tvSub.setText(item.subtitle);
            } else {
                ivh.tvSub.setVisibility(View.GONE);
            }

            ImageUtils.loadAvatar(context, item.avatarUrl, ivh.ivAvatar);

            ivh.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSearchSectionTitle);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivAvatar;
        TextView tvName;
        TextView tvSub;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivSearchAvatar);
            tvName = itemView.findViewById(R.id.tvSearchName);
            tvSub = itemView.findViewById(R.id.tvSearchSub);
        }
    }
}
