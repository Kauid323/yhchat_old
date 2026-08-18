package com.nago8.chat.old;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.adapter.VoicePlaylistAdapter;
import com.nago8.chat.old.model.VoicePlaylistItem;
import com.nago8.chat.old.utils.AudioPlayerManager;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.ThemeUtils;
import com.nago8.chat.old.utils.VoicePlaylistManager;

import java.util.List;

public class VoicePlaylistActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private VoicePlaylistAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private View layoutEmpty;
    private TextView tvItemCount;
    private AppCompatImageView ivPlayModeIcon;
    private TextView tvPlayModeText;

    private final VoicePlaylistManager.OnPlaylistChangeListener playlistListener = new VoicePlaylistManager.OnPlaylistChangeListener() {
        @Override
        public void onPlaylistChanged() {
            runOnUiThread(VoicePlaylistActivity.this::refreshList);
        }

        @Override
        public void onPlayModeChanged(VoicePlaylistManager.PlayMode mode) {
            runOnUiThread(VoicePlaylistActivity.this::updatePlayModeDisplay);
        }

        @Override
        public void onCurrentItemChanged(VoicePlaylistItem currentItem, int index) {
            runOnUiThread(() -> {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
    };

    private final AudioPlayerManager.GlobalAudioPlayListener audioPlayListener = (state, msgId, currentMs, totalMs) -> runOnUiThread(() -> {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_playlist);

        VoicePlaylistManager.getInstance().init(this);

        // 顶栏主题适配
        View topBar = findViewById(R.id.topBar);
        int primaryColor = ThemeUtils.getThemeColor(this);
        int fgColor = ThemeUtils.getContrastingForegroundColor(primaryColor);

        if (topBar != null) topBar.setBackgroundColor(primaryColor);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setColorFilter(fgColor);
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setTextColor(fgColor);

        TextView btnClearAll = findViewById(R.id.btnClearAll);
        if (btnClearAll != null) {
            btnClearAll.setTextColor(fgColor);
            btnClearAll.setOnClickListener(v -> confirmClearAll());
        }

        recyclerView = findViewById(R.id.recyclerView);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvItemCount = findViewById(R.id.tvItemCount);
        ivPlayModeIcon = findViewById(R.id.ivPlayModeIcon);
        tvPlayModeText = findViewById(R.id.tvPlayModeText);

        View btnTogglePlayMode = findViewById(R.id.btnTogglePlayMode);
        if (btnTogglePlayMode != null) {
            btnTogglePlayMode.setOnClickListener(v -> {
                VoicePlaylistManager.getInstance().toggleNextPlayMode();
                updatePlayModeDisplay();
            });
        }

        setupRecyclerView();
        updatePlayModeDisplay();
        refreshList();

        VoicePlaylistManager.getInstance().addListener(playlistListener);
        AudioPlayerManager.getInstance().addGlobalListener(audioPlayListener);
    }

    @Override
    protected void onDestroy() {
        VoicePlaylistManager.getInstance().removeListener(playlistListener);
        AudioPlayerManager.getInstance().removeGlobalListener(audioPlayListener);
        super.onDestroy();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VoicePlaylistAdapter(this, new VoicePlaylistAdapter.OnItemActionListener() {
            @Override
            public void onItemClick(VoicePlaylistItem item, int position) {
                VoicePlaylistManager.getInstance().playItem(VoicePlaylistActivity.this, position);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onItemDelete(VoicePlaylistItem item, int position) {
                VoicePlaylistManager.getInstance().removeItem(position);
            }

            @Override
            public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }

            @Override
            public void onItemMoved(int fromPosition, int toPosition) {
                VoicePlaylistManager.getInstance().moveItem(fromPosition, toPosition);
            }
        });
        recyclerView.setAdapter(adapter);

        // 拖拽排序与侧滑删除
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                adapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                adapter.removeItem(pos);
                VoicePlaylistManager.getInstance().removeItem(pos);
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void refreshList() {
        List<VoicePlaylistItem> items = VoicePlaylistManager.getInstance().getPlaylist();
        if (adapter != null) {
            adapter.setItems(items);
        }

        int count = items.size();
        if (tvItemCount != null) {
            tvItemCount.setText(getString(R.string.voice_playlist_item_count, count));
        }

        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        }
    }

    private void updatePlayModeDisplay() {
        VoicePlaylistManager.PlayMode mode = VoicePlaylistManager.getInstance().getPlayMode();
        int primaryColor = ThemeUtils.getThemeColor(this);

        if (ivPlayModeIcon != null) {
            ivPlayModeIcon.setColorFilter(primaryColor);
        }
        if (tvPlayModeText != null) {
            tvPlayModeText.setTextColor(primaryColor);
        }

        switch (mode) {
            case SEQUENCE:
                if (ivPlayModeIcon != null) ivPlayModeIcon.setImageResource(R.drawable.ic_repeat);
                if (tvPlayModeText != null) tvPlayModeText.setText(R.string.voice_play_mode_sequence);
                break;
            case SINGLE_LOOP:
                if (ivPlayModeIcon != null) ivPlayModeIcon.setImageResource(R.drawable.ic_repeat_one);
                if (tvPlayModeText != null) tvPlayModeText.setText(R.string.voice_play_mode_single_loop);
                break;
            case SHUFFLE:
                if (ivPlayModeIcon != null) ivPlayModeIcon.setImageResource(R.drawable.ic_shuffle);
                if (tvPlayModeText != null) tvPlayModeText.setText(R.string.voice_play_mode_shuffle);
                break;
        }
    }

    private void confirmClearAll() {
        if (VoicePlaylistManager.getInstance().getCount() == 0) return;

        ThemeUtils.showThemedDialog(
                new AlertDialog.Builder(this)
                        .setTitle(R.string.voice_playlist_clear_all)
                        .setMessage(R.string.voice_playlist_clear_confirm)
                        .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                            VoicePlaylistManager.getInstance().clear();
                            Toast.makeText(this, R.string.action_delete, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
        );
    }
}
