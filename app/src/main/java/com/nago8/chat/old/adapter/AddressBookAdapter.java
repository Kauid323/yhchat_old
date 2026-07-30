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

import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.model.AddressBookItem;
import com.nago8.chat.old.proto.user.address_book_list;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class AddressBookAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private final List<AddressBookItem> items = new ArrayList<>();

    public AddressBookAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<AddressBookItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<AddressBookItem> getItems() {
        return items;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == AddressBookItem.TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_address_book_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_address_book_entry, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AddressBookItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText(item.getHeaderTitle());
        } else if (holder instanceof ItemViewHolder) {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            address_book_list.Data.Data_list data = item.getData();
            if (data == null) return;

            // Display Name (Remark or Name)
            String displayName = item.getDisplayName();
            itemHolder.tvName.setText(displayName);

            // Subtitle logic (If remark exists, show original name as sub; otherwise show chat_id or hide)
            if (data.remark != null && !data.remark.trim().isEmpty() && data.name != null && !data.name.trim().isEmpty()) {
                itemHolder.tvSub.setText(data.name);
                itemHolder.tvSub.setVisibility(View.VISIBLE);
            } else {
                itemHolder.tvSub.setVisibility(View.GONE);
            }

            // Permission level for group chats
            int level = data.permisson_level;
            if (item.getChatType() == 2 && level > 0) {
                itemHolder.tvBadge.setVisibility(View.VISIBLE);
                if (level == 100) {
                    itemHolder.tvBadge.setText(context.getString(R.string.group_member_owner));
                } else if (level == 2) {
                    itemHolder.tvBadge.setText(context.getString(R.string.group_member_admin));
                } else {
                    itemHolder.tvBadge.setText("Lv." + level);
                }
            } else {
                itemHolder.tvBadge.setVisibility(View.GONE);
            }

            // No disturb icon
            boolean noDisturb = data.no_disturb;
            itemHolder.ivNoDisturb.setVisibility(noDisturb ? View.VISIBLE : View.GONE);

            // Avatar image loading
            ImageUtils.loadAvatar(context, data.avatar_url, itemHolder.ivAvatar);

            // Item click listener
            itemHolder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CHAT_ID, data.chat_id);
                intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, item.getChatType());
                intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, displayName);
                intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, data.avatar_url != null ? data.avatar_url : "");
                context.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvSectionHeader);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvSub;
        TextView tvBadge;
        ImageView ivNoDisturb;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvSub = itemView.findViewById(R.id.tvSub);
            tvBadge = itemView.findViewById(R.id.tvBadge);
            ivNoDisturb = itemView.findViewById(R.id.ivNoDisturb);
        }
    }
}
