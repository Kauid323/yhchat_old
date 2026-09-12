package com.nago8.chat.old;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.nago8.chat.old.model.StickerPack;
import com.nago8.chat.old.repository.StickerRepository;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StickerPackManagerActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private View layoutEmpty;
    private ProgressBar progressBar;

    private final List<StickerPack> packList = new ArrayList<>();
    private PackAdapter adapter;
    private final StickerRepository repository = new StickerRepository();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_manager);
        ThemeUtils.registerActivity(this);

        initViews();
        loadPacks(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPacks(false);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.sticker_manage_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerViewPacks);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        progressBar = findViewById(R.id.progressBar);

        int primaryColor = ThemeUtils.getThemeColor(this);
        swipeRefreshLayout.setColorSchemeColors(primaryColor);
        swipeRefreshLayout.setOnRefreshListener(() -> loadPacks(false));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PackAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.sticker_create_pack)
                .setIcon(R.drawable.ic_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            showCreatePackDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadPacks(boolean showLoading) {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.user_profile_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        if (showLoading) {
            progressBar.setVisibility(View.VISIBLE);
        }

        repository.listStickerPacks(token, new StickerRepository.StickerPacksCallback() {
            @Override
            public void onSuccess(List<StickerPack> packs) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    packList.clear();
                    if (packs != null) {
                        packList.addAll(packs);
                    }
                    adapter.notifyDataSetChanged();
                    layoutEmpty.setVisibility(packList.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(StickerPackManagerActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    layoutEmpty.setVisibility(packList.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void showCreatePackDialog() {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        int primaryColor = ThemeUtils.getThemeColor(this);

        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        til.setHint(getString(R.string.sticker_create_pack_name_hint));
        til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
        til.setBoxStrokeColor(primaryColor);
        til.setHintTextColor(android.content.res.ColorStateList.valueOf(primaryColor));

        TextInputEditText etName = new TextInputEditText(this);
        etName.setTextSize(14);
        til.addView(etName);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(4));
        container.addView(til);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sticker_create_pack)
                .setView(container)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, R.string.sticker_pack_name_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    repository.createStickerPack(token, name, new StickerRepository.CreatePackCallback() {
                        @Override
                        public void onSuccess(long packId) {
                            runOnUiThread(() -> {
                                Toast.makeText(StickerPackManagerActivity.this, R.string.sticker_create_success, Toast.LENGTH_SHORT).show();
                                loadPacks(false);
                                // 直接跳转到详情页以便添加表情
                                Intent intent = new Intent(StickerPackManagerActivity.this, StickerPackDetailActivity.class);
                                intent.putExtra(StickerPackDetailActivity.EXTRA_PACK_ID, packId);
                                startActivity(intent);
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> Toast.makeText(StickerPackManagerActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void confirmRemovePack(StickerPack pack) {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token) || pack == null) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sticker_remove_pack)
                .setMessage(getString(R.string.sticker_remove_pack_confirm_format, pack.name))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.removeStickerPack(token, pack.id, new StickerRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(StickerPackManagerActivity.this, R.string.sticker_remove_success, Toast.LENGTH_SHORT).show();
                                loadPacks(false);
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> Toast.makeText(StickerPackManagerActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void moveItem(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= packList.size() || toIndex < 0 || toIndex >= packList.size()) {
            return;
        }

        Collections.swap(packList, fromIndex, toIndex);
        adapter.notifyItemMoved(fromIndex, toIndex);

        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        repository.sortStickerPacks(token, packList, new StickerRepository.SimpleCallback() {
            @Override
            public void onSuccess() {}

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(StickerPackManagerActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private class PackAdapter extends RecyclerView.Adapter<PackAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sticker_pack_manage, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StickerPack pack = packList.get(position);
            holder.tvName.setText(pack.name);
            int count = pack.stickerItems != null ? pack.stickerItems.size() : 0;
            holder.tvCount.setText(getString(R.string.sticker_pack_count_format, count));

            String coverUrl = pack.getCoverUrl();
            if (!TextUtils.isEmpty(coverUrl)) {
                ImageUtils.loadSticker(StickerPackManagerActivity.this, coverUrl, holder.ivCover, 120, 120);
            } else {
                holder.ivCover.setImageResource(R.drawable.ic_image);
            }

            holder.btnMoveUp.setEnabled(position > 0);
            holder.btnMoveUp.setAlpha(position > 0 ? 1f : 0.3f);
            holder.btnMoveUp.setOnClickListener(v -> moveItem(holder.getAdapterPosition(), holder.getAdapterPosition() - 1));

            holder.btnMoveDown.setEnabled(position < packList.size() - 1);
            holder.btnMoveDown.setAlpha(position < packList.size() - 1 ? 1f : 0.3f);
            holder.btnMoveDown.setOnClickListener(v -> moveItem(holder.getAdapterPosition(), holder.getAdapterPosition() + 1));

            holder.btnRemove.setOnClickListener(v -> confirmRemovePack(pack));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(StickerPackManagerActivity.this, StickerPackDetailActivity.class);
                intent.putExtra(StickerPackDetailActivity.EXTRA_PACK_ID, pack.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return packList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivCover;
            TextView tvName;
            TextView tvCount;
            View btnMoveUp;
            View btnMoveDown;
            View btnRemove;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivCover = itemView.findViewById(R.id.ivPackCover);
                tvName = itemView.findViewById(R.id.tvPackName);
                tvCount = itemView.findViewById(R.id.tvPackCount);
                btnMoveUp = itemView.findViewById(R.id.btnMoveUp);
                btnMoveDown = itemView.findViewById(R.id.btnMoveDown);
                btnRemove = itemView.findViewById(R.id.btnRemovePack);
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
