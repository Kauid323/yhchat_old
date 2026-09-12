package com.nago8.chat.old.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.utils.FengEmojiRenderer;

import java.util.ArrayList;
import java.util.List;

public class EmojiGridAdapter extends BaseAdapter {

    public interface OnEmojiClickListener {
        void onEmojiClick(String emojiText);
    }

    private final Context context;
    private final List<String> emojis = new ArrayList<>();
    private final boolean isTwemoji;
    private OnEmojiClickListener listener;

    public EmojiGridAdapter(Context context, List<String> emojiList, boolean isTwemoji, OnEmojiClickListener listener) {
        this.context = context;
        if (emojiList != null) {
            this.emojis.addAll(emojiList);
        }
        this.isTwemoji = isTwemoji;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return emojis.size();
    }

    @Override
    public String getItem(int position) {
        return emojis.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv;
        if (convertView instanceof TextView) {
            tv = (TextView) convertView;
        } else {
            tv = new TextView(context);
            int size = dp(44);
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size));
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.bg_item_ripple);
        }

        String emoji = getItem(position);
        if (isTwemoji) {
            int iconSize = dp(28);
            CharSequence cs = FengEmojiRenderer.apply(context, emoji, iconSize);
            tv.setText(cs, TextView.BufferType.SPANNABLE);
        } else {
            tv.setTextSize(22);
            tv.setText(emoji);
        }

        tv.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEmojiClick(emoji);
            }
        });

        return tv;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
