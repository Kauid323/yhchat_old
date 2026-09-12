package com.nago8.chat.old.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class StickerGridAdapter extends RecyclerView.Adapter<StickerGridAdapter.ViewHolder> {

    public interface OnStickerClickListener {
        void onStickerClick(StickerItem item);
        void onStickerLongClick(StickerItem item);
    }

    private final Context context;
    private final List<StickerItem> items = new ArrayList<>();
    private final OnStickerClickListener listener;

    public StickerGridAdapter(Context context, List<StickerItem> itemList, OnStickerClickListener listener) {
        this.context = context;
        if (itemList != null) {
            this.items.addAll(itemList);
        }
        this.listener = listener;
    }

    public void setItems(List<StickerItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sticker_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StickerItem item = items.get(position);
        if (item != null) {
            String fullUrl = item.getFullUrl();
            if (!TextUtils.isEmpty(fullUrl)) {
                ImageUtils.loadSticker(context, fullUrl, holder.ivSticker, 150, 150);
            } else {
                holder.ivSticker.setImageResource(R.drawable.ic_image);
            }

            if (holder.tvName != null) {
                if (!TextUtils.isEmpty(item.name)) {
                    holder.tvName.setVisibility(View.VISIBLE);
                    holder.tvName.setText(item.name);
                } else {
                    holder.tvName.setVisibility(View.GONE);
                }
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStickerClick(item);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onStickerLongClick(item);
                }
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ImageView ivSticker;
        public final TextView tvName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.ivSticker = itemView.findViewById(R.id.ivSticker);
            this.tvName = itemView.findViewById(R.id.tvStickerName);
        }
    }
}
