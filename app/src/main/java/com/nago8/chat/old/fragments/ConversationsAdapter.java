package com.nago8.chat.old.fragments;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.cache.ConversationCache;
import com.nago8.chat.old.components.SwipeMenuLayout;
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
    private OnConversationActionListener actionListener;

    private String lastClickedChatId = "";
    private int clickCount = 0;

    public interface OnConversationActionListener {
        void onConversationClick(ConversationList.ConversationData data, int position);
        void onPinToggle(ConversationList.ConversationData data, boolean isSticky, int position);
        void onDeleteConversation(ConversationList.ConversationData data, int position);
    }

    public void setOnConversationActionListener(OnConversationActionListener listener) {
        this.actionListener = listener;
    }

    // Retain legacy method signature compatibility
    @SuppressWarnings("unused")
    public void setOnConversationClickListener(OnConversationClickListener listener) {
        if (listener != null) {
            this.actionListener = new OnConversationActionListener() {
                @Override
                public void onConversationClick(ConversationList.ConversationData data, int position) {
                    listener.onConversationClick(data, position);
                }

                @Override
                public void onPinToggle(ConversationList.ConversationData data, boolean isSticky, int position) {}

                @Override
                public void onDeleteConversation(ConversationList.ConversationData data, int position) {}
            };
        }
    }

    @SuppressWarnings("unused")
    public interface OnConversationClickListener {
        void onConversationClick(ConversationList.ConversationData data, int position);
    }

    public void setData(List<ConversationList.ConversationData> data) {
        if (data == null) data = new ArrayList<>();
        final List<ConversationList.ConversationData> oldList = new ArrayList<>(this.dataList);
        final List<ConversationList.ConversationData> newList = new ArrayList<>(data);

        boolean oldEmpty = oldList.isEmpty();
        boolean newEmpty = newList.isEmpty();

        this.dataList = newList;

        if (oldEmpty || newEmpty) {
            notifyDataSetChanged();
        } else {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ConversationDiffCallback(oldList, newList));
            diffResult.dispatchUpdatesTo(this);
        }
    }

    private static class ConversationDiffCallback extends DiffUtil.Callback {
        private final List<ConversationList.ConversationData> oldList;
        private final List<ConversationList.ConversationData> newList;

        public ConversationDiffCallback(List<ConversationList.ConversationData> oldList, List<ConversationList.ConversationData> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            ConversationList.ConversationData oldItem = oldList.get(oldItemPosition);
            ConversationList.ConversationData newItem = newList.get(newItemPosition);
            if (oldItem == null || newItem == null || oldItem.chat_id == null || newItem.chat_id == null) return false;
            return oldItem.chat_id.equals(newItem.chat_id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            ConversationList.ConversationData oldItem = oldList.get(oldItemPosition);
            ConversationList.ConversationData newItem = newList.get(newItemPosition);
            return oldItem.unread_message == newItem.unread_message
                    && oldItem.timestamp_ms == newItem.timestamp_ms
                    && oldItem.do_not_disturb == newItem.do_not_disturb
                    && android.text.TextUtils.equals(oldItem.chat_content, newItem.chat_content)
                    && android.text.TextUtils.equals(oldItem.name, newItem.name)
                    && android.text.TextUtils.equals(oldItem.avatar_url, newItem.avatar_url);
        }
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

    private boolean isHeader(ConversationList.ConversationData data) {
        return data.chat_id == null || data.chat_id.isEmpty();
    }

    @SuppressWarnings("unused")
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void onPushMessage(WsMsg wsMsg, Context ctx) {
        if (wsMsg == null || ctx == null) return;
        if (com.nago8.chat.old.ws.WsClient.isBlockedMessage(wsMsg)) return;

        String myUserId = com.nago8.chat.old.utils.PrefUtils.getUserId(ctx);
        String chatId = com.nago8.chat.old.ws.WsClient.getTargetChatId(wsMsg, myUserId);
        if (chatId == null || chatId.isEmpty()) return;

        int foundIndex = -1;
        for (int i = 0; i < dataList.size(); i++) {
            if (chatId.equals(dataList.get(i).chat_id)) {
                foundIndex = i;
                break;
            }
        }

        String senderName = (wsMsg.sender != null && wsMsg.sender.name != null) ? wsMsg.sender.name : "";
        String preview = WsMsgConverter.toPreviewText(wsMsg, ctx);
        String chatContent;
        if (preview != null && preview.startsWith("该消息已于")) {
            chatContent = preview;
        } else {
            chatContent = (!TextUtils.isEmpty(senderName)) ? senderName + ":" + preview : preview;
        }
        boolean isFromMe = (wsMsg.sender != null && wsMsg.sender.chat_id != null && wsMsg.sender.chat_id.equals(myUserId));

        ConversationList.ConversationData newData;
        if (foundIndex >= 0) {
            ConversationList.ConversationData oldData = dataList.get(foundIndex);
            int updatedUnread = isFromMe ? oldData.unread_message : (oldData.unread_message + 1);
            newData = oldData.newBuilder()
                    .unread_message(updatedUnread)
                    .chat_content(chatContent)
                    .timestamp_ms(wsMsg.timestamp)
                    .build();
        } else {
            String avatarUrl = (wsMsg.sender != null && wsMsg.sender.avatar_url != null) ? wsMsg.sender.avatar_url : "";
            newData = new ConversationList.ConversationData.Builder()
                    .chat_id(chatId)
                    .chat_type(wsMsg.chat_type != 0 ? wsMsg.chat_type : 1)
                    .name(senderName)
                    .avatar_url(avatarUrl)
                    .unread_message(isFromMe ? 0 : 1)
                    .chat_content(chatContent)
                    .timestamp_ms(wsMsg.timestamp)
                    .build();
        }

        if (foundIndex >= 0) {
            dataList.remove(foundIndex);
            dataList.add(0, newData);
        } else {
            dataList.add(0, newData);
        }
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
        if (h.swipeLayout != null) {
            h.swipeLayout.smoothClose();
        }

        h.tvName.setText(data.name);
        h.tvContent.setText(data.chat_content);
        if (data.chat_content != null && (data.chat_content.startsWith("该消息已于") || data.chat_content.contains("撤回"))) {
            h.tvContent.setAlpha(0.55f);
        } else {
            h.tvContent.setAlpha(1.0f);
        }
        if (data.timestamp_ms > 0) {
            h.tvTime.setVisibility(View.VISIBLE);
            h.tvTime.setText(TimeUtils.formatChatTime(data.timestamp_ms));
        } else {
            h.tvTime.setVisibility(View.GONE);
        }
        ImageUtils.loadAvatar(h.itemView.getContext(), data.avatar_url, h.ivAvatar);

        // 免打扰标识
        if (data.do_not_disturb == 1) {
            h.ivDnd.setVisibility(View.VISIBLE);
        } else {
            h.ivDnd.setVisibility(View.GONE);
        }

        if (data.unread_message > 0) {
            if (data.do_not_disturb == 1) {
                h.tvUnreadCount.setVisibility(View.GONE);
            } else {
                h.tvUnreadCount.setVisibility(View.VISIBLE);
                h.tvUnreadCount.setText(String.valueOf(data.unread_message));
            }
        } else {
            h.tvUnreadCount.setVisibility(View.GONE);
        }

        // 判断该会话 ID 是否已经被置顶
        boolean isSticky = ConversationCache.getInstance().isSticky(data.chat_id);

        if (h.btnPin != null) {
            if (isSticky) {
                h.btnPin.setText("取消置顶");
                h.btnPin.setBackgroundColor(0xFF6B7280);
            } else {
                h.btnPin.setText("置顶");
                h.btnPin.setBackgroundColor(0xFF3B82F6);
            }

            h.btnPin.setOnClickListener(v -> {
                if (h.swipeLayout != null) h.swipeLayout.smoothClose();
                if (actionListener != null) {
                    actionListener.onPinToggle(data, isSticky, holder.getAdapterPosition());
                }
            });
        }

        if (h.btnDelete != null) {
            h.btnDelete.setOnClickListener(v -> {
                if (h.swipeLayout != null) h.swipeLayout.smoothClose();
                if (actionListener != null) {
                    actionListener.onDeleteConversation(data, holder.getAdapterPosition());
                }
            });
        }

        h.rootView.setOnClickListener(v -> {
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
                if (actionListener != null) {
                    actionListener.onConversationClick(data, holder.getAdapterPosition());
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
        SwipeMenuLayout swipeLayout;
        View rootView;
        ImageView ivAvatar;
        TextView tvName, tvContent, tvTime, tvUnreadCount, btnPin, btnDelete;
        androidx.appcompat.widget.AppCompatImageView ivDnd;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            swipeLayout = itemView.findViewById(R.id.swipeLayout);
            rootView = itemView.findViewById(R.id.rootView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            ivDnd = itemView.findViewById(R.id.ivDnd);
            btnPin = itemView.findViewById(R.id.btnPin);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
