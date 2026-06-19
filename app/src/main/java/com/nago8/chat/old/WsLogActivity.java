package com.nago8.chat.old;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.ws.WsLogManager;

import java.util.ArrayList;
import java.util.List;

public class WsLogActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private LogAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refreshLogs;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ws_log);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        AppCompatImageButton btnClear = findViewById(R.id.btnClear);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);

        btnBack.setOnClickListener(v -> onBackPressed());
        btnClear.setOnClickListener(v -> {
            WsLogManager.getInstance().clear();
            refreshLogs();
        });

        adapter = new LogAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLogs();
        handler.postDelayed(refreshRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void refreshLogs() {
        List<WsLogManager.LogEntry> logs = WsLogManager.getInstance().getLogs();
        adapter.setLogs(logs);
        tvEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
        if (!logs.isEmpty()) {
            recyclerView.scrollToPosition(logs.size() - 1);
        }
        handler.postDelayed(refreshRunnable, 1000);
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {
        private final List<WsLogManager.LogEntry> logs = new ArrayList<>();

        void setLogs(List<WsLogManager.LogEntry> newLogs) {
            logs.clear();
            if (newLogs != null) logs.addAll(newLogs);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(12);
            tv.setPadding(24, 12, 24, 12);
            tv.setTextIsSelectable(true);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WsLogManager.LogEntry entry = logs.get(position);
            TextView tv = (TextView) holder.itemView;

            int color;
            switch (entry.direction) {
                case "SEND": color = 0xFF1565C0; break;
                case "RECV": color = 0xFF2E7D32; break;
                case "ERROR": color = 0xFFC62828; break;
                default: color = 0xFF616161; break;
            }
            tv.setTextColor(color);
            tv.setText(entry.formattedTime() + " [" + entry.direction + "] " + entry.message);
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
