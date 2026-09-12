package com.nago8.chat.old.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.utils.FengEmojiRenderer;

import java.util.ArrayList;
import java.util.List;

public class EmojiGridAdapter extends RecyclerView.Adapter<EmojiGridAdapter.ViewHolder> {

    public interface OnEmojiClickListener {
        void onEmojiClick(String emojiText);
    }

    private final Context context;
    private final List<String> emojis = new ArrayList<>();
    private final boolean isTwemoji;
    private OnEmojiClickListener listener;
    private final int itemSizePx;
    private final int twemojiIconSizePx;

    public EmojiGridAdapter(Context context, List<String> emojiList, boolean isTwemoji, OnEmojiClickListener listener) {
        this.context = context;
        if (emojiList != null) {
            this.emojis.addAll(emojiList);
        }
        this.isTwemoji = isTwemoji;
        this.listener = listener;
        this.itemSizePx = dp(44);
        this.twemojiIconSizePx = dp(28);

        if (isTwemoji) {
            FengEmojiRenderer.prewarmCache(context, twemojiIconSizePx);
        }
    }

    public void setItems(List<String> newEmojis) {
        emojis.clear();
        if (newEmojis != null) {
            emojis.addAll(newEmojis);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (isTwemoji) {
            FrameLayout container = new FrameLayout(context);
            container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemSizePx));
            container.setBackgroundResource(R.drawable.bg_item_ripple);

            ImageView iv = new ImageView(context);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(twemojiIconSizePx, twemojiIconSizePx, Gravity.CENTER);
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            container.addView(iv);

            return new ViewHolder(container, null, iv);
        } else {
            TextView tv = new TextView(context);
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemSizePx));
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.bg_item_ripple);
            tv.setTextSize(22);
            return new ViewHolder(tv, tv, null);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String emoji = emojis.get(position);
        if (isTwemoji && holder.iv != null) {
            Bitmap bitmap = FengEmojiRenderer.getBitmap(context, emoji, twemojiIconSizePx);
            if (bitmap != null) {
                holder.iv.setImageBitmap(bitmap);
            } else {
                holder.iv.setImageDrawable(null);
            }
        } else if (holder.tv != null) {
            holder.tv.setText(emoji);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEmojiClick(emoji);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emojis.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView tv;
        public final ImageView iv;

        public ViewHolder(@NonNull View itemView, TextView tv, ImageView iv) {
            super(itemView);
            this.tv = tv;
            this.iv = iv;
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
