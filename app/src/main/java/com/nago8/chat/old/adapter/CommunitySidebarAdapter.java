package com.nago8.chat.old.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;

import java.util.List;

public class CommunitySidebarAdapter extends RecyclerView.Adapter<CommunitySidebarAdapter.SidebarViewHolder> {

    public static class SidebarItem {
        public final int typ;
        public final String title;

        public SidebarItem(int typ, String title) {
            this.typ = typ;
            this.title = title;
        }
    }

    private final Context context;
    private final List<SidebarItem> items;
    private int selectedPosition = 0;
    private OnSidebarItemSelectedListener listener;

    public interface OnSidebarItemSelectedListener {
        void onSidebarItemSelected(SidebarItem item, int position);
    }

    public CommunitySidebarAdapter(Context context, List<SidebarItem> items) {
        this.context = context;
        this.items = items;
    }

    public void setOnSidebarItemSelectedListener(OnSidebarItemSelectedListener listener) {
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        if (position >= 0 && position < items.size() && selectedPosition != position) {
            int oldPos = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(oldPos);
            notifyItemChanged(selectedPosition);
        }
    }

    @NonNull
    @Override
    public SidebarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_community_sidebar, parent, false);
        return new SidebarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SidebarViewHolder holder, int position) {
        SidebarItem item = items.get(position);
        boolean isSelected = (position == selectedPosition);
        holder.bind(item, isSelected);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    class SidebarViewHolder extends RecyclerView.ViewHolder {
        private final View layoutSidebarRoot;
        private final View indicatorSidebar;
        private final TextView tvSidebarTitle;

        public SidebarViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutSidebarRoot = itemView.findViewById(R.id.layoutSidebarRoot);
            indicatorSidebar = itemView.findViewById(R.id.indicatorSidebar);
            tvSidebarTitle = itemView.findViewById(R.id.tvSidebarTitle);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    setSelectedPosition(pos);
                    if (listener != null) {
                        listener.onSidebarItemSelected(items.get(pos), pos);
                    }
                }
            });
        }

        public void bind(SidebarItem item, boolean isSelected) {
            tvSidebarTitle.setText(item.title);
            if (isSelected) {
                indicatorSidebar.setVisibility(View.VISIBLE);
                tvSidebarTitle.setTypeface(null, Typeface.BOLD);
                tvSidebarTitle.setTextColor(ContextCompat.getColor(context, R.color.app_primary));
                layoutSidebarRoot.setBackgroundColor(0x0A000000);
            } else {
                indicatorSidebar.setVisibility(View.GONE);
                tvSidebarTitle.setTypeface(null, Typeface.NORMAL);
                tvSidebarTitle.setTextColor(ContextCompat.getColor(context, android.R.color.tab_indicator_text));
                layoutSidebarRoot.setBackgroundColor(0x00000000);
            }
        }
    }
}
