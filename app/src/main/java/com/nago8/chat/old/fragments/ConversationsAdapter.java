package com.nago8.chat.old.fragments;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.TimeUtils;
import com.nago8.chat.old.utils.WsMsgConverter;

import java.util.ArrayList;
import java.util.List;

public class ConversationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

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
            if (isHeader(oldData)) return;
            ConversationList.ConversationData newData = oldData.newBuilder()
                    .unread_message(0)
                    .build();
            dataList.set(position, newData);
            notifyItemChanged(position);
        }
    }

    /**
     * 判断是否为搜索分组标题项（chat_id 为空即为标题）
     */
    private boolean isHeader(ConversationList.ConversationData data) {
        return data.chat_id == null || data.chat_id.length() == 0;
    }

    /**
     * 收到 WS 推送消息时调用：
     * 根据 wsMsg.chat_id 找到对应会话，未读数 +1，
     * 预览内容更新为 "{sender.name}:{preview}"，
     * 并将该会话移到列表顶部。
     */
    public void onPushMessage(WsMsg wsMsg, Context ctx) {
        if (wsMsg == null || wsMsg.chat_id == null || ctx == null) return;

        String chatId = wsMsg.chat_id;
        int foundIndex = -1;
        for (int i = 0; i < dataList.size(); i++) {
            if (chatId.equals(dataList.get(i).chat_id)) {
                foundIndex = i;
                break;
            }
        }

        // 会话不存在于列表中，忽略（等下次拉取列表时会出现）
        if (foundIndex < 0) return;

        ConversationList.ConversationData oldData = dataList.get(foundIndex);

        // 构建预览文本: "{sender.name}:{preview}"
        String senderName = (wsMsg.sender != null && wsMsg.sender.name != null)
                ? wsMsg.sender.name : "";
        String preview = WsMsgConverter.toPreviewText(wsMsg, ctx);
        String chatContent = senderName + ":" + preview;

        // 更新会话数据：未读+1、预览内容、时间戳
        ConversationList.ConversationData newData = oldData.newBuilder()
                .unread_message(oldData.unread_message + 1)
                .chat_content(chatContent)
                .timestamp_ms(wsMsg.timestamp)
                .build();

        // 移到顶部
        dataList.remove(foundIndex);
        dataList.add(0, newData);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return isHeader(dataList.get(position)) ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ConversationList.ConversationData data = dataList.get(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText(data.name);
            return;
        }

        ItemViewHolder h = (ItemViewHolder) holder;
        h.tvName.setText(data.name);
        h.tvContent.setText(data.chat_content);
        if (data.timestamp_ms > 0) {
            h.tvTime.setVisibility(View.VISIBLE);
            h.tvTime.setText(TimeUtils.formatChatTime(data.timestamp_ms));
        } else {
            h.tvTime.setVisibility(View.GONE);
        }
        ImageUtils.loadAvatar(h.itemView.getContext(), data.avatar_url, h.ivAvatar);

        if (data.unread_message > 0) {
            h.tvUnreadCount.setVisibility(View.VISIBLE);
            h.tvUnreadCount.setText(String.valueOf(data.unread_message));
        } else {
            h.tvUnreadCount.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
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

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvHeader);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvContent, tvTime, tvUnreadCount;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
        }
    }
}