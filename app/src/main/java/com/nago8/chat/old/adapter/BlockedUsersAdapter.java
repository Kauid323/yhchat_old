package com.nago8.chat.old.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.nago8.chat.old.R;
import com.nago8.chat.old.UserProfileActivity;
import com.nago8.chat.old.model.BlockedUserModel;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class BlockedUsersAdapter extends RecyclerView.Adapter<BlockedUsersAdapter.ViewHolder> {

    public interface OnUnblockClickListener {
        void onUnblockClick(BlockedUserModel item, int position);
    }

    private final Context context;
    private final List<BlockedUserModel> list = new ArrayList<>();
    private OnUnblockClickListener listener;

    public BlockedUsersAdapter(Context context) {
        this.context = context;
    }

    public void setOnUnblockClickListener(OnUnblockClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<BlockedUserModel> newItems) {
        this.list.clear();
        if (newItems != null) {
            this.list.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < list.size()) {
            list.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, list.size() - position);
        }
    }

    public int getItemCountList() {
        return list.size();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_blocked_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BlockedUserModel item = list.get(position);
        holder.bind(item);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivAvatar;
        private final TextView tvNickname;
        private final TextView tvUserId;
        private final MaterialButton btnUnblock;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvNickname = itemView.findViewById(R.id.tvNickname);
            tvUserId = itemView.findViewById(R.id.tvUserId);
            btnUnblock = itemView.findViewById(R.id.btnUnblock);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < list.size()) {
                    BlockedUserModel item = list.get(pos);
                    if (!TextUtils.isEmpty(item.getUserId())) {
                        Intent intent = new Intent(context, UserProfileActivity.class);
                        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, item.getUserId());
                        context.startActivity(intent);
                    }
                }
            });
        }

        public void bind(BlockedUserModel item) {
            if (item == null) return;
            tvNickname.setText(item.getNickname());
            tvUserId.setText(context.getString(R.string.user_id_format, item.getUserId()));
            ImageUtils.loadAvatar(context, item.getAvatarUrl(), ivAvatar);

            btnUnblock.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onUnblockClick(item, pos);
                }
            });
        }
    }
}
