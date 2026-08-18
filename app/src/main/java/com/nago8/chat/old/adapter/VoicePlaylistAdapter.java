package com.nago8.chat.old.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.VoicePlaylistItem;
import com.nago8.chat.old.utils.AudioPlayerManager;
import com.nago8.chat.old.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class VoicePlaylistAdapter extends RecyclerView.Adapter<VoicePlaylistAdapter.ViewHolder> {

    public interface OnItemActionListener {
        void onItemClick(VoicePlaylistItem item, int position);
        void onItemDelete(VoicePlaylistItem item, int position);
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
        void onItemMoved(int fromPosition, int toPosition);
    }

    private final Context context;
    private final List<VoicePlaylistItem> items = new ArrayList<>();
    private final OnItemActionListener listener;
    private int primaryColor;

    public VoicePlaylistAdapter(Context context, OnItemActionListener listener) {
        this.context = context;
        this.listener = listener;
        this.primaryColor = ThemeUtils.getThemeColor(context);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<VoicePlaylistItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<VoicePlaylistItem> getItems() {
        return items;
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= items.size() || toPosition < 0 || toPosition >= items.size()) {
            return;
        }
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(items, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        if (listener != null) {
            listener.onItemMoved(fromPosition, toPosition);
        }
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, items.size() - position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_voice_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoicePlaylistItem item = items.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIndex;
        private final AppCompatImageView ivPlayingIcon;
        private final TextView tvTitle;
        private final TextView tvDuration;
        private final TextView tvSubtitle;
        private final View btnDelete;
        private final View ivDragHandle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tvIndex);
            ivPlayingIcon = itemView.findViewById(R.id.ivPlayingIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            ivDragHandle = itemView.findViewById(R.id.ivDragHandle);
        }

        @SuppressLint("ClickableViewAccessibility")
        public void bind(VoicePlaylistItem item, int position) {
            String currentPlayingMsgId = AudioPlayerManager.getInstance().getCurrentMsgId();
            boolean isPlaying = item.msgId != null && item.msgId.equals(currentPlayingMsgId);

            if (isPlaying) {
                tvIndex.setVisibility(View.GONE);
                ivPlayingIcon.setVisibility(View.VISIBLE);
                ivPlayingIcon.setColorFilter(primaryColor);
                tvTitle.setTextColor(primaryColor);
            } else {
                tvIndex.setVisibility(View.VISIBLE);
                tvIndex.setText(String.valueOf(position + 1));
                ivPlayingIcon.setVisibility(View.GONE);
                tvTitle.setTextColor(com.nago8.chat.old.utils.PrefUtils.isDarkModeEnabled(context) ? 0xFFFFFFFF : 0xFF212121);
            }

            tvTitle.setText(item.title != null && !item.title.isEmpty() ? item.title : context.getString(R.string.message_voice));
            tvDuration.setText(formatDuration(item.durationSec));
            tvDuration.setTextColor(primaryColor);
            tvSubtitle.setText(item.subtitle != null ? item.subtitle : "");

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item, getAdapterPosition());
                }
            });

            btnDelete.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemDelete(item, pos);
                }
            });

            ivDragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && listener != null) {
                    listener.onStartDrag(ViewHolder.this);
                }
                return false;
            });
        }
    }

    private static String formatDuration(int durationSec) {
        if (durationSec < 0) durationSec = 0;
        int min = durationSec / 60;
        int sec = durationSec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }
}
