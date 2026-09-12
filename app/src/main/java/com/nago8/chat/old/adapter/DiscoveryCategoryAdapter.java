package com.nago8.chat.old.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.DiscoveryModels;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.chip.Chip;

public class DiscoveryCategoryAdapter extends RecyclerView.Adapter<DiscoveryCategoryAdapter.CategoryViewHolder> {

    private final Context context;
    private final List<DiscoveryModels.CategoryItem> list = new ArrayList<>();
    private int selectedCategoryId = 0;
    private OnCategorySelectedListener listener;

    public interface OnCategorySelectedListener {
        void onCategorySelected(DiscoveryModels.CategoryItem category);
    }

    public DiscoveryCategoryAdapter(Context context) {
        this.context = context;
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    public void setData(List<DiscoveryModels.CategoryItem> categories) {
        this.list.clear();
        // 默认首项为"全部"
        this.list.add(new DiscoveryModels.CategoryItem(0, "全部"));
        if (categories != null) {
            this.list.addAll(categories);
        }
        notifyDataSetChanged();
    }

    public void setSelectedCategoryId(int categoryId) {
        this.selectedCategoryId = categoryId;
        notifyDataSetChanged();
    }

    public int getSelectedCategoryId() {
        return selectedCategoryId;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_discovery_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        DiscoveryModels.CategoryItem item = list.get(position);
        holder.chipCategory.setText(item.name != null ? item.name : "");
        holder.chipCategory.setChecked(item.id == selectedCategoryId);

        holder.chipCategory.setOnClickListener(v -> {
            if (selectedCategoryId != item.id) {
                selectedCategoryId = item.id;
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onCategorySelected(item);
                }
            } else {
                holder.chipCategory.setChecked(true);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        Chip chipCategory;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            chipCategory = itemView.findViewById(R.id.chipCategory);
        }
    }
}
