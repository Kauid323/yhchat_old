package com.nago8.chat.old;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;

import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONObject;

public class CreatePostActivity extends AppCompatActivity {

    public static final String EXTRA_BA_ID = "ba_id";
    public static final String EXTRA_BA_NAME = "ba_name";
    public static final String EXTRA_DRAFT_ID = "draft_id";
    public static final String EXTRA_POST_ID = "post_id";
    public static final String EXTRA_INITIAL_TITLE = "initial_title";
    public static final String EXTRA_INITIAL_CONTENT = "initial_content";
    public static final String EXTRA_INITIAL_CONTENT_TYPE = "initial_content_type";

    public static final int FORMAT_PLAIN_TEXT = 1;
    public static final int FORMAT_MARKDOWN = 2;

    private int baId;
    private String baName;
    private long draftId;
    private long editPostId;
    private int selectedContentType = FORMAT_PLAIN_TEXT;

    private TextView tvTitle;
    private EditText etPostTitle;
    private EditText etPostContent;
    private TextView tvFormatSelector;
    private TextView btnSaveDraft;
    private TextView btnSend;
    private TextView tvSectionTarget;
    private TextView tvWordCount;
    private FrameLayout loadingOverlay;
    private TextView tvLoadingText;

    private CommunityRepository communityRepository;
    private boolean isOperating = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        baId = getIntent().getIntExtra(EXTRA_BA_ID, 0);
        baName = getIntent().getStringExtra(EXTRA_BA_NAME);
        draftId = getIntent().getLongExtra(EXTRA_DRAFT_ID, 0);
        editPostId = getIntent().getLongExtra(EXTRA_POST_ID, 0);

        communityRepository = new CommunityRepository();

        initViews();
        setupFormatSelector();
        setupContentListener();

        if (editPostId > 0) {
            tvTitle.setText(R.string.create_post_title_edit_post);
            btnSend.setText(R.string.action_save);
            btnSaveDraft.setVisibility(View.GONE);

            String initialTitle = getIntent().getStringExtra(EXTRA_INITIAL_TITLE);
            String initialContent = getIntent().getStringExtra(EXTRA_INITIAL_CONTENT);
            int initialContentType = getIntent().getIntExtra(EXTRA_INITIAL_CONTENT_TYPE, FORMAT_PLAIN_TEXT);

            if (initialTitle != null) etPostTitle.setText(initialTitle);
            if (initialContent != null) {
                etPostContent.setText(initialContent);
                etPostContent.setSelection(initialContent.length());
            }
            selectedContentType = (initialContentType == FORMAT_MARKDOWN) ? FORMAT_MARKDOWN : FORMAT_PLAIN_TEXT;
            updateFormatUI();
            updateSectionTargetUI();
        } else if (draftId > 0) {
            tvTitle.setText(R.string.create_post_title_edit_draft);
            loadDraft();
        }
    }

    private void initViews() {
        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tvFormatSelector = findViewById(R.id.tvFormatSelector);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnSend = findViewById(R.id.btnSend);
        etPostTitle = findViewById(R.id.etPostTitle);
        etPostContent = findViewById(R.id.etPostContent);
        tvSectionTarget = findViewById(R.id.tvSectionTarget);
        tvWordCount = findViewById(R.id.tvWordCount);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        btnBack.setOnClickListener(v -> onBackPressed());
        btnSaveDraft.setOnClickListener(v -> saveDraft());
        btnSend.setOnClickListener(v -> submitPost());

        updateSectionTargetUI();
        updateFormatUI();
    }

    private void updateSectionTargetUI() {
        if (!TextUtils.isEmpty(baName)) {
            tvSectionTarget.setText(getString(R.string.create_post_target_format, baName));
            tvSectionTarget.setVisibility(View.VISIBLE);
        } else if (baId > 0) {
            tvSectionTarget.setText(getString(R.string.create_post_target_format, "ID: " + baId));
            tvSectionTarget.setVisibility(View.VISIBLE);
        } else {
            tvSectionTarget.setVisibility(View.GONE);
        }
    }

    private void setupFormatSelector() {
        View layoutFormatSelector = findViewById(R.id.layoutFormatSelector);
        if (layoutFormatSelector != null) {
            layoutFormatSelector.setOnClickListener(v -> showFormatPopupMenu(layoutFormatSelector));
        }
    }

    private void showFormatPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, FORMAT_PLAIN_TEXT, 0, R.string.create_post_format_text);
        popup.getMenu().add(0, FORMAT_MARKDOWN, 1, R.string.create_post_format_markdown);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == FORMAT_PLAIN_TEXT || id == FORMAT_MARKDOWN) {
                if (selectedContentType != id) {
                    selectedContentType = id;
                    updateFormatUI();
                }
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void updateFormatUI() {
        if (selectedContentType == FORMAT_MARKDOWN) {
            tvFormatSelector.setText(getString(R.string.create_post_format_markdown) + " ▾");
            etPostContent.setHint(R.string.create_post_content_hint_markdown);
        } else {
            tvFormatSelector.setText(getString(R.string.create_post_format_text) + " ▾");
            etPostContent.setHint(R.string.create_post_content_hint_text);
        }
    }

    private void setupContentListener() {
        etPostContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s != null ? s.length() : 0;
                tvWordCount.setText(getString(R.string.create_post_word_count_format, length));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadDraft() {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true, getString(R.string.create_post_loading_draft));

        communityRepository.getDraft(token, baId, draftId, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    setLoadingState(false, null);
                    try {
                        JSONObject root = new JSONObject(responseBody);
                        int code = root.optInt("code", 0);
                        if (code == 1 && root.has("data")) {
                            JSONObject data = root.getJSONObject("data");
                            JSONObject postObj = data.optJSONObject("post");
                            if (postObj == null) {
                                postObj = data.optJSONObject("posts");
                            }
                            if (postObj == null) {
                                postObj = data;
                            }

                            String title = postObj.optString("title", "");
                            String content = postObj.optString("content", "");
                            int contentType = postObj.optInt("contentType", FORMAT_PLAIN_TEXT);
                            int postBaId = postObj.optInt("baId", 0);

                            if (postBaId > 0) {
                                baId = postBaId;
                            }

                            JSONObject groupObj = postObj.optJSONObject("group");
                            if (groupObj != null && groupObj.has("name")) {
                                String groupName = groupObj.optString("name", "");
                                if (!TextUtils.isEmpty(groupName)) {
                                    baName = groupName;
                                }
                            }

                            etPostTitle.setText(title);
                            etPostContent.setText(content);
                            if (!TextUtils.isEmpty(content)) {
                                etPostContent.setSelection(content.length());
                            }

                            selectedContentType = (contentType == FORMAT_MARKDOWN) ? FORMAT_MARKDOWN : FORMAT_PLAIN_TEXT;
                            updateFormatUI();
                            updateSectionTargetUI();

                            if (baId > 0 && TextUtils.isEmpty(baName)) {
                                fetchBaName(token, baId);
                            }
                        } else {
                            String msg = root.optString("msg", "");
                            if (TextUtils.isEmpty(msg)) msg = getString(R.string.operation_failed);
                            Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_draft_load_failed, msg), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_draft_load_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    setLoadingState(false, null);
                    Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_draft_load_failed, msg), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchBaName(String token, int baId) {
        communityRepository.getBaInfo(token, baId, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    try {
                        JSONObject root = new JSONObject(responseBody);
                        if (root.optInt("code", 0) == 1 && root.has("data")) {
                            JSONObject data = root.getJSONObject("data");
                            JSONObject baObj = data.optJSONObject("ba");
                            if (baObj == null) baObj = data;
                            String name = baObj.optString("title", baObj.optString("name", ""));
                            if (!TextUtils.isEmpty(name)) {
                                baName = name;
                                updateSectionTargetUI();
                            }
                        }
                    } catch (Exception ignored) {}
                });
            }

            @Override
            public void onError(String msg) {}
        });
    }

    private void saveDraft() {
        if (isOperating) return;

        String title = etPostTitle.getText().toString().trim();
        String content = etPostContent.getText().toString().trim();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            Toast.makeText(this, R.string.create_post_draft_empty_tip, Toast.LENGTH_SHORT).show();
            return;
        }

        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true, getString(R.string.create_post_saving_draft));

        communityRepository.createDraft(token, baId, title, content, selectedContentType, draftId, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    setLoadingState(false, null);
                    try {
                        JSONObject root = new JSONObject(responseBody);
                        int code = root.optInt("code", 0);
                        if (code == 1) {
                            JSONObject data = root.optJSONObject("data");
                            if (data != null && data.has("id")) {
                                draftId = data.optLong("id", draftId);
                            }
                            Toast.makeText(CreatePostActivity.this, R.string.create_post_draft_saved, Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            String msg = root.optString("msg", "");
                            if (TextUtils.isEmpty(msg)) msg = getString(R.string.operation_failed);
                            Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_save_draft_failed, msg), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_save_draft_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    setLoadingState(false, null);
                    Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_save_draft_failed, msg), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void submitPost() {
        if (isOperating) return;

        String title = etPostTitle.getText().toString().trim();
        String content = etPostContent.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, R.string.create_post_title_empty, Toast.LENGTH_SHORT).show();
            etPostTitle.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, R.string.create_post_content_empty, Toast.LENGTH_SHORT).show();
            etPostContent.requestFocus();
            return;
        }

        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        if (editPostId > 0) {
            // 编辑已有文章模式
            setLoadingState(true, getString(R.string.create_post_submitting));
            communityRepository.editPost(token, editPostId, title, content, selectedContentType, new CommunityRepository.StringCallback() {
                @Override
                public void onSuccess(String responseBody) {
                    runOnUiThread(() -> {
                        try {
                            JSONObject root = new JSONObject(responseBody);
                            int code = root.optInt("code", 0);
                            if (code == 1) {
                                Toast.makeText(CreatePostActivity.this, R.string.create_post_edit_success, Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                                finish();
                            } else {
                                setLoadingState(false, null);
                                String msg = root.optString("msg", "");
                                if (TextUtils.isEmpty(msg)) {
                                    msg = getString(R.string.operation_failed);
                                }
                                Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_edit_failed, msg), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            setLoadingState(false, null);
                            Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_edit_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        setLoadingState(false, null);
                        Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_edit_failed, msg), Toast.LENGTH_SHORT).show();
                    });
                }
            });
            return;
        }

        // 新建文章模式
        if (baId <= 0) {
            Toast.makeText(this, R.string.create_post_section_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true, getString(R.string.create_post_submitting));

        communityRepository.createPost(token, baId, title, content, selectedContentType, draftId, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    try {
                        JSONObject root = new JSONObject(responseBody);
                        int code = root.optInt("code", 0);
                        if (code == 1) {
                            Toast.makeText(CreatePostActivity.this, R.string.create_post_success, Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            setLoadingState(false, null);
                            String msg = root.optString("msg", "");
                            if (TextUtils.isEmpty(msg)) {
                                msg = getString(R.string.operation_failed);
                            }
                            Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_failed_format, msg), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        setLoadingState(false, null);
                        Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    setLoadingState(false, null);
                    Toast.makeText(CreatePostActivity.this, getString(R.string.create_post_failed_format, msg), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setLoadingState(boolean loading, String loadingText) {
        this.isOperating = loading;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (tvLoadingText != null && loadingText != null) {
            tvLoadingText.setText(loadingText);
        }
        if (btnSend != null) {
            btnSend.setEnabled(!loading);
            btnSend.setAlpha(loading ? 0.5f : 1.0f);
        }
        if (btnSaveDraft != null && editPostId == 0) {
            btnSaveDraft.setEnabled(!loading);
            btnSaveDraft.setAlpha(loading ? 0.5f : 1.0f);
        }
    }
}
