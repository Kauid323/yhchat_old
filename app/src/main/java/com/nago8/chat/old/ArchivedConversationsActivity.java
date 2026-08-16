package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.nago8.chat.old.cache.ArchiveManager;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;

import java.util.ArrayList;
import java.util.List;

public class ArchivedConversationsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View layoutEmpty;
    private ArchivedAdapter adapter;

    @Override
    protected void attachBaseContext(@NonNull Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_conversations);

        initViews();
        loadArchivedData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ArchivedAdapter(new ArchivedAdapter.OnArchivedActionListener() {
            @Override
            public void onItemClick(ArchiveManager.ArchivedConversation item) {
                if (item == null || item.chatId == null || item.chatId.isEmpty()) return;
                Intent intent = new Intent(ArchivedConversationsActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CHAT_ID, item.chatId);
                intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, item.chatType != 0 ? item.chatType : 1);
                intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, item.name);
                intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, item.avatarUrl);
                startActivity(intent);
            }

            @Override
            public void onUnarchive(ArchiveManager.ArchivedConversation item, int position) {
                if (item == null || item.chatId == null || item.chatId.isEmpty()) return;
                ArchiveManager.getInstance().unarchiveConversation(ArchivedConversationsActivity.this, item.chatId);
                Toast.makeText(ArchivedConversationsActivity.this, R.string.conversation_unarchived_toast, Toast.LENGTH_SHORT).show();
                adapter.removeItem(position);
                checkEmptyState();
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void loadArchivedData() {
        List<ArchiveManager.ArchivedConversation> list = ArchiveManager.getInstance().getArchivedList(this);
        adapter.setData(list);
        checkEmptyState();
    }

    private void checkEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private static class ArchivedAdapter extends RecyclerView.Adapter<ArchivedAdapter.ViewHolder> {

        interface OnArchivedActionListener {
            void onItemClick(ArchiveManager.ArchivedConversation item);
            void onUnarchive(ArchiveManager.ArchivedConversation item, int position);
        }

        private final List<ArchiveManager.ArchivedConversation> dataList = new ArrayList<>();
        private final OnArchivedActionListener listener;

        public ArchivedAdapter(OnArchivedActionListener listener) {
            this.listener = listener;
        }

        public void setData(List<ArchiveManager.ArchivedConversation> list) {
            dataList.clear();
            if (list != null) {
                dataList.addAll(list);
            }
            notifyDataSetChanged();
        }

        public void removeItem(int position) {
            if (position >= 0 && position < dataList.size()) {
                dataList.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, dataList.size() - position);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_archived_conversation, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ArchiveManager.ArchivedConversation item = dataList.get(position);
            holder.tvName.setText(item.name != null && !item.name.isEmpty() ? item.name : item.chatId);
            holder.tvContent.setText(item.lastContent != null ? item.lastContent : "");
            ImageUtils.loadAvatar(holder.itemView.getContext(), item.avatarUrl, holder.ivAvatar);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });

            holder.btnUnarchive.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUnarchive(item, holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            TextView tvContent;
            MaterialButton btnUnarchive;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvName = itemView.findViewById(R.id.tvName);
                tvContent = itemView.findViewById(R.id.tvContent);
                btnUnarchive = itemView.findViewById(R.id.btnUnarchive);
            }
        }
    }
}
