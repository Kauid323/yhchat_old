package com.nago8.chat.old.widget;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nago8.chat.old.CustomInstructionActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.adapter.ChatInstructionAdapter;
import com.nago8.chat.old.model.ChatInstruction;
import com.nago8.chat.old.repository.MessageRepository;
import com.nago8.chat.old.utils.PrefUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;

public class ChatInstructionBottomSheetDialog extends BottomSheetDialog {

    public interface OnInstructionSelectCallback {
        void onSendInstruction(ChatInstruction instruction, String paramText);
    }

    private final String chatId;
    private final int chatType; // 2-群聊, 3-机器人
    private final MessageRepository repository;
    private final OnInstructionSelectCallback callback;

    private ProgressBar pbLoading;
    private TextView tvEmpty;
    private RecyclerView rvInstructions;
    private com.google.android.material.tabs.TabLayout tabLayoutBots;
    private ChatInstructionAdapter adapter;
    private Call runningCall;

    private List<ChatInstruction> allInstructions = new ArrayList<>();

    public ChatInstructionBottomSheetDialog(@NonNull Context context, String chatId, int chatType, OnInstructionSelectCallback callback) {
        super(context);
        this.chatId = chatId;
        this.chatType = chatType;
        this.callback = callback;
        this.repository = new MessageRepository();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_chat_instruction_sheet, null);
        setContentView(view);

        pbLoading = view.findViewById(R.id.pbLoading);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        rvInstructions = view.findViewById(R.id.rvInstructions);
        tabLayoutBots = view.findViewById(R.id.tabLayoutBots);

        rvInstructions.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatInstructionAdapter(getContext(), this::showInstructionConfirmOrSend);
        rvInstructions.setAdapter(adapter);

        loadInstructions();
    }

    private void loadInstructions() {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (tabLayoutBots != null) tabLayoutBots.setVisibility(View.GONE);

        String token = PrefUtils.getToken(getContext());
        MessageRepository.InstructionCallback icb = new MessageRepository.InstructionCallback() {
            @Override
            public void onSuccess(List<ChatInstruction> instructions) {
                if (!isShowing()) return;
                rvInstructions.post(() -> {
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                    allInstructions = instructions != null ? instructions : new ArrayList<>();
                    if (allInstructions.isEmpty()) {
                        if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                        if (tabLayoutBots != null) tabLayoutBots.setVisibility(View.GONE);
                        adapter.setData(null);
                    } else {
                        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
                        setupBotTabs(allInstructions);
                        filterInstructions(null);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                if (!isShowing()) return;
                rvInstructions.post(() -> {
                    if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                    if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                    if (tabLayoutBots != null) tabLayoutBots.setVisibility(View.GONE);
                });
            }
        };

        if (chatType == 2) {
            // 群聊指令
            runningCall = repository.getGroupInstructions(token, chatId, icb);
        } else {
            // 机器人指令
            runningCall = repository.getBotInstructions(token, chatId, icb);
        }
    }

    private static class BotItem {
        final String botId;
        final String botName;
        BotItem(String botId, String botName) {
            this.botId = botId;
            this.botName = botName;
        }
    }

    private void setupBotTabs(List<ChatInstruction> instructions) {
        if (tabLayoutBots == null) return;
        tabLayoutBots.removeAllTabs();

        // 收集所有不重复的机器人
        List<BotItem> bots = new ArrayList<>();
        Map<String, Boolean> seenBots = new HashMap<>();

        for (ChatInstruction item : instructions) {
            String botId = item.botId != null ? item.botId : "";
            if (!seenBots.containsKey(botId) && !botId.isEmpty()) {
                seenBots.put(botId, true);
                String botName = !TextUtils.isEmpty(item.botName) ? item.botName : botId;
                bots.add(new BotItem(botId, botName));
            }
        }

        // 如果机器人数量大于 1，展示分类 Tab
        if (bots.size() > 1) {
            tabLayoutBots.setVisibility(View.VISIBLE);

            int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(getContext());
            int unselectedColor = androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_secondary);
            tabLayoutBots.setSelectedTabIndicatorColor(primaryColor);
            tabLayoutBots.setTabTextColors(unselectedColor, primaryColor);

            // 添加 "全部" Tab
            com.google.android.material.tabs.TabLayout.Tab allTab = tabLayoutBots.newTab();
            allTab.setText(R.string.tab_all);
            allTab.setTag(null);
            tabLayoutBots.addTab(allTab);

            // 为每个机器人添加 Tab
            for (BotItem bot : bots) {
                com.google.android.material.tabs.TabLayout.Tab tab = tabLayoutBots.newTab();
                tab.setText(bot.botName);
                tab.setTag(bot.botId);
                tabLayoutBots.addTab(tab);
            }

            tabLayoutBots.clearOnTabSelectedListeners();
            tabLayoutBots.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    String selectedBotId = (String) tab.getTag();
                    filterInstructions(selectedBotId);
                }

                @Override
                public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            });
        } else {
            tabLayoutBots.setVisibility(View.GONE);
        }
    }

    private void filterInstructions(String targetBotId) {
        if (allInstructions == null || allInstructions.isEmpty()) {
            adapter.setData(null);
            return;
        }

        if (TextUtils.isEmpty(targetBotId)) {
            // 显示全部
            adapter.setData(allInstructions);
        } else {
            List<ChatInstruction> filtered = new ArrayList<>();
            for (ChatInstruction item : allInstructions) {
                if (targetBotId.equals(item.botId)) {
                    filtered.add(item);
                }
            }
            adapter.setData(filtered);
        }
    }

    private void showInstructionConfirmOrSend(ChatInstruction instruction) {
        dismiss();
        if (instruction == null) return;

        // 1. 自定义指令 / 表单指令：打开单独的配置与执行界面
        if (instruction.isCustomFormCommand()) {
            Intent intent = new Intent(getContext(), CustomInstructionActivity.class);
            intent.putExtra(CustomInstructionActivity.EXTRA_INSTRUCTION, instruction);
            intent.putExtra(CustomInstructionActivity.EXTRA_CHAT_ID, chatId);
            intent.putExtra(CustomInstructionActivity.EXTRA_CHAT_TYPE, chatType);
            getContext().startActivity(intent);
            return;
        }

        // 2. 直发指令：无需参数，点击直接发送
        if (instruction.isDirectCommand()) {
            if (callback != null) {
                callback.onSendInstruction(instruction, instruction.defaultText);
            }
            return;
        }

        // 3. 普通指令：弹出 Material 风格参数输入弹窗
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_instruction_input, null);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogCommandTitle);
        TextView tvDesc = dialogView.findViewById(R.id.tvDialogCommandDesc);
        com.google.android.material.textfield.TextInputLayout tilParam = dialogView.findViewById(R.id.tilCommandParam);
        com.google.android.material.textfield.TextInputEditText etParam = dialogView.findViewById(R.id.etCommandParam);

        int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(getContext());
        tvTitle.setText(String.format(getContext().getString(R.string.chat_instruction_item_format), instruction.name));
        tvTitle.setTextColor(primaryColor);
        tvDesc.setText(instruction.desc);

        if (tilParam != null) {
            int strokeColor = (primaryColor & 0x00FFFFFF) | 0x88000000;
            android.content.res.ColorStateList boxStrokeColorStateList = new android.content.res.ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_focused},
                            new int[]{}
                    },
                    new int[]{
                            primaryColor,
                            strokeColor
                    }
            );
            tilParam.setBoxStrokeColorStateList(boxStrokeColorStateList);
            tilParam.setHintTextColor(android.content.res.ColorStateList.valueOf(primaryColor));
            tilParam.setDefaultHintTextColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_secondary)));

            if (!TextUtils.isEmpty(instruction.hintText)) {
                tilParam.setHint(instruction.hintText);
            }
        }

        if (etParam != null && !TextUtils.isEmpty(instruction.defaultText)) {
            etParam.setText(instruction.defaultText);
            etParam.setSelection(instruction.defaultText.length());
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                .setView(dialogView)
                .setPositiveButton(R.string.chat_instruction_send, (dialog, which) -> {
                    String text = etParam != null && etParam.getText() != null ? etParam.getText().toString().trim() : "";
                    if (callback != null) {
                        callback.onSendInstruction(instruction, text);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    @Override
    public void onDetachedFromWindow() {
        if (runningCall != null) {
            runningCall.cancel();
            runningCall = null;
        }
        super.onDetachedFromWindow();
    }
}
