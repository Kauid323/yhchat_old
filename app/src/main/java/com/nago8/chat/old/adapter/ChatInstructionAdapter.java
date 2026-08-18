package com.nago8.chat.old.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.R;
import com.nago8.chat.old.model.ChatInstruction;

import java.util.ArrayList;
import java.util.List;

public class ChatInstructionAdapter extends RecyclerView.Adapter<ChatInstructionAdapter.ViewHolder> {

    public interface OnInstructionClickListener {
        void onInstructionClick(ChatInstruction instruction);
    }

    private final Context context;
    private final List<ChatInstruction> list = new ArrayList<>();
    private final OnInstructionClickListener listener;

    public ChatInstructionAdapter(Context context, OnInstructionClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<ChatInstruction> items) {
        list.clear();
        if (items != null) {
            list.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_instruction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatInstruction item = list.get(position);
        holder.tvName.setText(item.name);
        holder.tvDesc.setText(item.desc);

        int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(context);
        if (holder.tvCommandPrefix != null) {
            holder.tvCommandPrefix.setTextColor(primaryColor);
        }

        if (!TextUtils.isEmpty(item.botName)) {
            holder.tvBotName.setVisibility(View.VISIBLE);
            holder.tvBotName.setText(item.botName);
            applyThemeBadgeStyle(holder.tvBotName, primaryColor);
        } else if (item.isCustomFormCommand()) {
            holder.tvBotName.setVisibility(View.VISIBLE);
            holder.tvBotName.setText(R.string.custom_instruction_title);
            applyThemeBadgeStyle(holder.tvBotName, primaryColor);
        } else {
            holder.tvBotName.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onInstructionClick(item);
            }
        });
    }

    private void applyThemeBadgeStyle(TextView tvBadge, int primaryColor) {
        tvBadge.setTextColor(primaryColor);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(4));
        int bgColor = (primaryColor & 0x00FFFFFF) | 0x22000000;
        int strokeColor = (primaryColor & 0x00FFFFFF) | 0x44000000;
        gd.setColor(bgColor);
        gd.setStroke(dp(1), strokeColor);
        tvBadge.setBackground(gd);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCommandPrefix;
        TextView tvName;
        TextView tvDesc;
        TextView tvBotName;

        ViewHolder(View itemView) {
            super(itemView);
            tvCommandPrefix = itemView.findViewById(R.id.tvCommandPrefix);
            tvName = itemView.findViewById(R.id.tvInstructionName);
            tvDesc = itemView.findViewById(R.id.tvInstructionDesc);
            tvBotName = itemView.findViewById(R.id.tvBotName);
        }
    }
}
