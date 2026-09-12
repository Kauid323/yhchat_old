package com.nago8.chat.old.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class StickerGridAdapter extends BaseAdapter {

    public interface OnStickerClickListener {
        void onStickerClick(StickerItem item);
        void onStickerLongClick(StickerItem item);
    }

    private final Context context;
    private List<StickerItem> items;
    private OnStickerClickListener listener;

    public StickerGridAdapter(Context context, List<StickerItem> itemList, OnStickerClickListener listener) {
        this.context = context;
        this.items = itemList != null ? itemList : new ArrayList<>();
        this.listener = listener;
    }

    public void setItems(List<StickerItem> newItems) {
        if (newItems != null) {
            this.items = newItems;
        } else {
            this.items = new ArrayList<>();
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items != null ? items.size() : 0;
    }

    @Override
    public StickerItem getItem(int position) {
        return (items != null && position >= 0 && position < items.size()) ? items.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_sticker_grid, parent, false);
            holder = new ViewHolder();
            holder.ivSticker = convertView.findViewById(R.id.ivSticker);
            holder.tvName = convertView.findViewById(R.id.tvStickerName);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        StickerItem item = getItem(position);
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

            convertView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStickerClick(item);
                }
            });

            convertView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onStickerLongClick(item);
                }
                return true;
            });
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivSticker;
        TextView tvName;
    }
}
