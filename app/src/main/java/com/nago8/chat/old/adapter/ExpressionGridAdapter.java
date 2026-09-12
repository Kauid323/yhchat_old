package com.nago8.chat.old.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.Expression;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class ExpressionGridAdapter extends RecyclerView.Adapter<ExpressionGridAdapter.ViewHolder> {

    public interface OnExpressionClickListener {
        void onExpressionClick(Expression expression);
        void onExpressionLongClick(Expression expression);
    }

    private final Context context;
    private final List<Expression> items = new ArrayList<>();
    private final OnExpressionClickListener listener;

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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sticker_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Expression item = items.get(position);
        if (item != null) {
            String fullUrl = item.getFullUrl();
            if (!TextUtils.isEmpty(fullUrl)) {
                ImageUtils.loadSticker(context, fullUrl, holder.ivSticker, 150, 150);
            } else {
                holder.ivSticker.setImageResource(R.drawable.ic_image);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExpressionClick(item);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onExpressionLongClick(item);
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

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.ivSticker = itemView.findViewById(R.id.ivSticker);
        }
    }
}
