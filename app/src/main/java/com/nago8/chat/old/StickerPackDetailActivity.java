package com.nago8.chat.old;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.model.StickerPack;
import com.nago8.chat.old.repository.StickerRepository;
import com.nago8.chat.old.utils.ImageUploadUtils;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.ThemeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class StickerPackDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PACK_ID = "extra_pack_id";
    private static final int REQUEST_PICK_IMAGE = 1001;

    private long packId;
    private StickerPack currentPack;
    private final StickerRepository repository = new StickerRepository();

    private Toolbar toolbar;
    private TextView tvName;
    private TextView tvDetailId;
    private View layoutCreator;
    private ImageView ivCreatorAvatar;
    private TextView tvCreatorName;
    private TextView tvStatCount;
    private TextView tvStatUsers;
    private TextView tvStatDate;
    private MaterialButton btnCollectPack;
    private MaterialButton btnAddStickerItem;
    private RecyclerView recyclerViewStickers;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    private DetailStickerAdapter adapter;
    private final List<StickerItem> stickerItems = new ArrayList<>();
    private boolean isOwner = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_detail);
        ThemeUtils.registerActivity(this);

        packId = parsePackIdFromIntent(getIntent());
        if (packId <= 0) {
            Toast.makeText(this, R.string.sticker_pack_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadDetail();
    }

    private long parsePackIdFromIntent(Intent intent) {
        if (intent == null) return 0;
        long id = intent.getLongExtra(EXTRA_PACK_ID, 0);
        if (id <= 0) id = intent.getLongExtra("sticker_pack_id", 0);
        if (id <= 0) id = intent.getLongExtra("pack_id", 0);
        if (id <= 0) id = intent.getLongExtra("id", 0);
        if (id <= 0) {
            String str = intent.getStringExtra(EXTRA_PACK_ID);
            if (TextUtils.isEmpty(str)) str = intent.getStringExtra("sticker_pack_id");
            if (TextUtils.isEmpty(str)) str = intent.getStringExtra("pack_id");
            if (TextUtils.isEmpty(str)) str = intent.getStringExtra("id");
            if (!TextUtils.isEmpty(str)) {
                try {
                    id = Long.parseLong(str.trim());
                } catch (Exception ignored) {}
            }
        }
        return id;
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.sticker_pack_detail_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvName = findViewById(R.id.tvDetailName);
        tvDetailId = findViewById(R.id.tvDetailId);
        layoutCreator = findViewById(R.id.layoutCreator);
        ivCreatorAvatar = findViewById(R.id.ivCreatorAvatar);
        tvCreatorName = findViewById(R.id.tvCreatorName);
        tvStatCount = findViewById(R.id.tvStatCount);
        tvStatUsers = findViewById(R.id.tvStatUsers);
        tvStatDate = findViewById(R.id.tvStatDate);
        btnCollectPack = findViewById(R.id.btnCollectPack);
        btnAddStickerItem = findViewById(R.id.btnAddStickerItem);
        recyclerViewStickers = findViewById(R.id.recyclerViewStickers);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        int primaryColor = ThemeUtils.getThemeColor(this);
        btnCollectPack.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
        btnCollectPack.setTextColor(ThemeUtils.getContrastingForegroundColor(primaryColor));
        btnAddStickerItem.setTextColor(primaryColor);
        btnAddStickerItem.setIconTint(ColorStateList.valueOf(primaryColor));

        recyclerViewStickers.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerViewStickers.setNestedScrollingEnabled(false);
        adapter = new DetailStickerAdapter();
        recyclerViewStickers.setAdapter(adapter);

        btnAddStickerItem.setOnClickListener(v -> pickImageForSticker());
        btnCollectPack.setOnClickListener(v -> toggleCollectPack());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isOwner) {
            menu.add(0, 1, 0, R.string.sticker_rename_pack);
            menu.add(0, 2, 0, R.string.sticker_delete_pack);
            menu.add(0, 3, 0, R.string.sticker_add_new_item);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            showRenamePackDialog();
            return true;
        } else if (item.getItemId() == 2) {
            confirmDeletePack();
            return true;
        } else if (item.getItemId() == 3) {
            pickImageForSticker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadDetail() {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        progressBar.setVisibility(View.VISIBLE);
        repository.getStickerPackDetail(token, packId, new StickerRepository.StickerPackDetailCallback() {
            @Override
            public void onSuccess(StickerPack pack) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    currentPack = pack;
                    bindPackData(pack);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(StickerPackDetailActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void bindPackData(StickerPack pack) {
        if (pack == null) return;

        String myUserId = PrefUtils.getUserId(this);
        String creatorId = !TextUtils.isEmpty(pack.creatorUserId) ? pack.creatorUserId : pack.createBy;
        isOwner = !TextUtils.isEmpty(myUserId) && myUserId.equals(creatorId);
        invalidateOptionsMenu();

        // Title
        tvName.setText(pack.name);
        tvName.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("name", pack.name));
                Toast.makeText(this, R.string.sticker_copied_name, Toast.LENGTH_SHORT).show();
            }
        });

        // Pack ID
        tvDetailId.setText(getString(R.string.sticker_id_format, String.valueOf(pack.id)));
        tvDetailId.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("id", String.valueOf(pack.id)));
                Toast.makeText(this, R.string.sticker_copied_id, Toast.LENGTH_SHORT).show();
            }
        });

        // Creator
        String authorName = !TextUtils.isEmpty(pack.creatorNickname) ? pack.creatorNickname : pack.createBy;
        if (!TextUtils.isEmpty(authorName) || !TextUtils.isEmpty(pack.creatorAvatarUrl)) {
            layoutCreator.setVisibility(View.VISIBLE);
            tvCreatorName.setText(!TextUtils.isEmpty(authorName) ? authorName : creatorId);
            if (!TextUtils.isEmpty(pack.creatorAvatarUrl)) {
                ImageUtils.loadAvatar(this, pack.creatorAvatarUrl, ivCreatorAvatar);
            } else {
                ivCreatorAvatar.setImageResource(R.drawable.ic_contacts);
            }
            final String targetUserId = !TextUtils.isEmpty(creatorId) ? creatorId : authorName;
            layoutCreator.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(targetUserId)) {
                    Intent intent = new Intent(StickerPackDetailActivity.this, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, targetUserId);
                    startActivity(intent);
                }
            });
        } else {
            layoutCreator.setVisibility(View.GONE);
        }

        // Stats: count, users, date
        int count = pack.stickerItems != null ? pack.stickerItems.size() : 0;
        tvStatCount.setText(String.valueOf(count));
        tvStatUsers.setText(String.valueOf(pack.userCount));

        if (pack.createTime > 0) {
            long tsMs = pack.createTime > 100000000000L ? pack.createTime : (pack.createTime * 1000L);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            tvStatDate.setText(sdf.format(new java.util.Date(tsMs)));
        } else {
            tvStatDate.setText("-");
        }

        // Action Buttons
        if (isOwner) {
            btnCollectPack.setVisibility(View.GONE);
            btnAddStickerItem.setVisibility(View.VISIBLE);
        } else {
            btnCollectPack.setVisibility(View.VISIBLE);
            btnAddStickerItem.setVisibility(View.GONE);
        }

        stickerItems.clear();
        if (pack.stickerItems != null) {
            stickerItems.addAll(pack.stickerItems);
        }
        adapter.setItems(stickerItems);
        tvEmpty.setVisibility(stickerItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toggleCollectPack() {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token) || currentPack == null) return;

        repository.addStickerPack(token, currentPack.id, new StickerRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(StickerPackDetailActivity.this, R.string.sticker_collect_success, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(StickerPackDetailActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void pickImageForSticker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.action_choose_image)), REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadAndAddSticker(data.getData());
        }
    }

    private void uploadAndAddSticker(Uri imageUri) {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.sticker_uploading, Toast.LENGTH_SHORT).show();

        ImageUploadUtils.getQiniuUploadToken(token, new ImageUploadUtils.TokenCallback() {
            @Override
            public void onSuccess(String qiniuToken) {
                ImageUploadUtils.uploadImage(StickerPackDetailActivity.this, imageUri, qiniuToken, new ImageUploadUtils.UploadCallback() {
                    @Override
                    public void onSuccess(ImageUploadUtils.QiniuResult result) {
                        String key = result.key;
                        repository.addStickerItem(token, packId, "sticker", key, new StickerRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    Toast.makeText(StickerPackDetailActivity.this, R.string.sticker_add_success, Toast.LENGTH_SHORT).show();
                                    loadDetail();
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(StickerPackDetailActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(StickerPackDetailActivity.this, getString(R.string.chat_image_upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(StickerPackDetailActivity.this, getString(R.string.chat_image_upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showStickerActionDialog(StickerItem item) {
        String[] actions = {
                getString(R.string.sticker_rename_item),
                getString(R.string.sticker_delete_item)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(item.name != null ? item.name : getString(R.string.message_sticker))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showRenameStickerDialog(item);
                    } else if (which == 1) {
                        confirmDeleteSticker(item);
                    }
                })
                .show();
    }

    private void showRenameStickerDialog(StickerItem item) {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        int primaryColor = ThemeUtils.getThemeColor(this);

        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        til.setHint(getString(R.string.sticker_rename_item_hint));
        til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
        til.setBoxStrokeColor(primaryColor);
        til.setHintTextColor(ColorStateList.valueOf(primaryColor));

        TextInputEditText etName = new TextInputEditText(this);
        etName.setText(item.name);
        etName.setTextSize(14);
        til.addView(etName);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(4));
        container.addView(til);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sticker_rename_item)
                .setView(container)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    String newName = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(newName)) return;

                    repository.renameStickerItem(token, item.id, newName, new StickerRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(StickerPackDetailActivity.this, R.string.sticker_rename_success, Toast.LENGTH_SHORT).show();
                                loadDetail();
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> Toast.makeText(StickerPackDetailActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void confirmDeleteSticker(StickerItem item) {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sticker_delete_item)
                .setMessage(R.string.sticker_delete_item_confirm)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.removeStickerItem(token, item.id, new StickerRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(StickerPackDetailActivity.this, R.string.sticker_delete_success, Toast.LENGTH_SHORT).show();
                                loadDetail();
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> Toast.makeText(StickerPackDetailActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showRenamePackDialog() {
        if (currentPack == null) return;
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        int primaryColor = ThemeUtils.getThemeColor(this);

        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        til.setHint(getString(R.string.sticker_rename_pack_hint));
        til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
        til.setBoxStrokeColor(primaryColor);
        til.setHintTextColor(ColorStateList.valueOf(primaryColor));

        TextInputEditText etName = new TextInputEditText(this);
        etName.setText(currentPack.name);
        etName.setTextSize(14);
        til.addView(etName);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(4));
        container.addView(til);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sticker_rename_pack)
                .setView(container)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                    String newName = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(newName)) return;

                    repository.renameStickerPack(token, currentPack.id, newName, new StickerRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(StickerPackDetailActivity.this, R.string.sticker_rename_success, Toast.LENGTH_SHORT).show();
                                loadDetail();
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> Toast.makeText(StickerPackDetailActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void confirmDeletePack() {
        if (currentPack == null) return;
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sticker_delete_pack)
                .setMessage(getString(R.string.sticker_delete_pack_confirm_format, currentPack.name))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deleteStickerPack(token, currentPack.id, new StickerRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(StickerPackDetailActivity.this, R.string.sticker_delete_pack_success, Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> Toast.makeText(StickerPackDetailActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class DetailStickerAdapter extends RecyclerView.Adapter<DetailStickerAdapter.ViewHolder> {

        public void setItems(List<StickerItem> newItems) {
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sticker_grid, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position < 0 || position >= stickerItems.size()) return;
            StickerItem item = stickerItems.get(position);
            if (item == null) return;

            String fullUrl = item.getFullUrl();
            if (!TextUtils.isEmpty(fullUrl)) {
                ImageUtils.loadSticker(StickerPackDetailActivity.this, fullUrl, holder.ivSticker, 150, 150);
            } else {
                holder.ivSticker.setImageResource(R.drawable.ic_image);
            }

            if (holder.tvName != null) {
                if (!TextUtils.isEmpty(item.name)) {
                    holder.tvName.setVisibility(View.VISIBLE);
                    holder.tvName.setText(item.name);
                } else {
                    holder.tvName.setVisibility(View.GONE);
                }
            }

            holder.itemView.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(item.getFullUrl())) {
                    ArrayList<String> urls = new ArrayList<>();
                    int startIdx = 0;
                    for (int i = 0; i < stickerItems.size(); i++) {
                        urls.add(stickerItems.get(i).getFullUrl());
                        if (stickerItems.get(i).id == item.id) {
                            startIdx = i;
                        }
                    }
                    Intent intent = new Intent(StickerPackDetailActivity.this, ImagePreviewActivity.class);
                    intent.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, urls);
                    intent.putExtra(ImagePreviewActivity.EXTRA_START_INDEX, startIdx);
                    startActivity(intent);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (isOwner) {
                    showStickerActionDialog(item);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return stickerItems != null ? stickerItems.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivSticker;
            TextView tvName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivSticker = itemView.findViewById(R.id.ivSticker);
                tvName = itemView.findViewById(R.id.tvStickerName);
            }
        }
    }
}
