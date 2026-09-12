package com.nago8.chat.old.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.Expression;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class ExpressionGridAdapter extends BaseAdapter {

    public interface OnExpressionClickListener {
        void onExpressionClick(Expression expression);
        void onExpressionLongClick(Expression expression);
    }

    private final Context context;
    private final List<Expression> items = new ArrayList<>();
    private OnExpressionClickListener listener;

    public ExpressionGridAdapter(Context context, List<Expression> itemList, OnExpressionClickListener listener) {
        this.context = context;
        if (itemList != null) {
            this.items.addAll(itemList);
        }
        this.listener = listener;
    }

    public void setItems(List<Expression> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Expression getItem(int position) {
        return items.get(position);
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
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Expression item = getItem(position);
        if (item != null) {
            String fullUrl = item.getFullUrl();
            if (!TextUtils.isEmpty(fullUrl)) {
                ImageUtils.loadSticker(context, fullUrl, holder.ivSticker, 150, 150);
            } else {
                holder.ivSticker.setImageResource(R.drawable.ic_image);
            }

            convertView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExpressionClick(item);
                }
            });

            convertView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onExpressionLongClick(item);
                }
                return true;
            });
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivSticker;
    }
}
