package com.nago8.chat.old.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.BotProfileActivity;
import com.nago8.chat.old.GroupProfileActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.model.DiscoveryModels;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class DiscoveryBannerAdapter extends RecyclerView.Adapter<DiscoveryBannerAdapter.BannerViewHolder> {

    private final Context context;
    private final List<DiscoveryModels.BannerItem> list = new ArrayList<>();
    private OnBannerClickListener listener;

    public interface OnBannerClickListener {
        void onBannerClick(DiscoveryModels.BannerItem item);
    }

    public DiscoveryBannerAdapter(Context context) {
        this.context = context;
    }

    public void setOnBannerClickListener(OnBannerClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<DiscoveryModels.BannerItem> data) {
        this.list.clear();
        if (data != null) {
            this.list.addAll(data);
        }
        notifyDataSetChanged();
    }

    public DiscoveryModels.BannerItem getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_discovery_banner, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        DiscoveryModels.BannerItem item = list.get(position);
        holder.tvBannerTitle.setText(item.title != null ? item.title : "");
        if (item.introduction != null && !item.introduction.trim().isEmpty()) {
            holder.tvBannerDesc.setText(item.introduction);
            holder.tvBannerDesc.setVisibility(View.VISIBLE);
        } else {
            holder.tvBannerDesc.setVisibility(View.GONE);
        }

        if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
            ImageUtils.loadImage(context, item.imageUrl, holder.ivBannerImage, 800, 360);
        } else {
            holder.ivBannerImage.setImageDrawable(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBannerClick(item);
                return;
            }
            handleBannerDefaultClick(item);
        });
    }

    private void handleBannerDefaultClick(DiscoveryModels.BannerItem item) {
        if (item == null) return;
        try {
            if (item.targetUrl != null && (item.targetUrl.startsWith("http://") || item.targetUrl.startsWith("https://"))) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.targetUrl));
                context.startActivity(intent);
            } else if (item.targetId != null && !item.targetId.trim().isEmpty()) {
                if (item.typ == 1) {
                    Intent intent = new Intent(context, GroupProfileActivity.class);
                    intent.putExtra(GroupProfileActivity.EXTRA_GROUP_ID, item.targetId);
                    context.startActivity(intent);
                } else {
                    Intent intent = new Intent(context, BotProfileActivity.class);
                    intent.putExtra(BotProfileActivity.EXTRA_BOT_ID, item.targetId);
                    context.startActivity(intent);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBannerImage;
        TextView tvBannerTitle;
        TextView tvBannerDesc;

        public BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBannerImage = itemView.findViewById(R.id.ivBannerImage);
            tvBannerTitle = itemView.findViewById(R.id.tvBannerTitle);
            tvBannerDesc = itemView.findViewById(R.id.tvBannerDesc);
        }
    }
}
