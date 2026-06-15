package com.nago8.chat.old.fragments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ViewHolder> {

    private List<ConversationList.ConversationData> dataList = new ArrayList<>();
    private OnConversationClickListener clickListener;
    
    private String lastClickedChatId = "";
    private int clickCount = 0;

    public interface OnConversationClickListener {
        void onConversationClick(ConversationList.ConversationData data, int position);
    }

    public void setOnConversationClickListener(OnConversationClickListener listener) {
        this.clickListener = listener;
    }

    public void setData(List<ConversationList.ConversationData> data) {
        this.dataList = new ArrayList<>(data);
        notifyDataSetChanged();
    }
    
    public void markAsRead(int position) {
        if (position >= 0 && position < dataList.size()) {
            ConversationList.ConversationData oldData = dataList.get(position);
            ConversationList.ConversationData newData = oldData.newBuilder()
                    .unread_message(0)
                    .build();
            dataList.set(position, newData);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConversationList.ConversationData data = dataList.get(position);
        holder.tvName.setText(data.name);
        holder.tvContent.setText(data.chat_content);
        holder.tvTime.setText(TimeUtils.formatChatTime(data.timestamp_ms));
        ImageUtils.loadAvatar(holder.itemView.getContext(), data.avatar_url, holder.ivAvatar);

        if (data.unread_message > 0) {
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
            holder.tvUnreadCount.setText(String.valueOf(data.unread_message));
        } else {
            holder.tvUnreadCount.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (data.chat_id.equals(lastClickedChatId)) {
                clickCount++;
            } else {
                lastClickedChatId = data.chat_id;
                clickCount = 1;
            }

            if (clickCount >= 5) {
                Toast.makeText(v.getContext(), "别戳了___*( ￣皿￣)/#____", Toast.LENGTH_SHORT).show();
                clickCount = 0;
            } else {
                if (clickListener != null) {
                    clickListener.onConversationClick(data, position);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvContent, tvTime, tvUnreadCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
        }
    }
}