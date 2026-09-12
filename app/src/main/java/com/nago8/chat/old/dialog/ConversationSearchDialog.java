package com.nago8.chat.old.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.adapter.ConversationSearchResultAdapter;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ConversationSearchDialog extends Dialog {

    private static final String TAG = "ConvSearchDialog";

    private final Activity activity;
    private View searchOverlayRoot;
    private MaterialCardView cardSearchContainer;
    private ImageView btnBackSearch;
    private EditText etSearchInput;
    private ProgressBar pbSearchLoading;
    private ImageView ivClearSearchText;
    private View viewSearchDivider;
    private RecyclerView rvSearchResults;
    private View layoutSearchEmpty;

    private ConversationSearchResultAdapter adapter;
    private Call currentCall;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public ConversationSearchDialog(@NonNull Activity activity) {
        super(activity, R.style.FloatingSearchDialogTheme);
        this.activity = activity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_conversation_search);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.TOP);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }

        initViews();
        setupEvents();
    }

    private void initViews() {
        searchOverlayRoot = findViewById(R.id.searchOverlayRoot);
        cardSearchContainer = findViewById(R.id.cardSearchContainer);
        btnBackSearch = findViewById(R.id.btnBackSearch);
        etSearchInput = findViewById(R.id.etSearchInput);
        pbSearchLoading = findViewById(R.id.pbSearchLoading);
        ivClearSearchText = findViewById(R.id.ivClearSearchText);
        viewSearchDivider = findViewById(R.id.viewSearchDivider);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        layoutSearchEmpty = findViewById(R.id.layoutSearchEmpty);

        adapter = new ConversationSearchResultAdapter(activity);
        rvSearchResults.setLayoutManager(new LinearLayoutManager(activity));
        rvSearchResults.setAdapter(adapter);

        // 点击外部遮罩关闭
        if (searchOverlayRoot != null) {
            searchOverlayRoot.setOnClickListener(v -> dismiss());
        }
        if (cardSearchContainer != null) {
            cardSearchContainer.setOnClickListener(v -> {}); // 阻断点击传递
        }
    }

    private void setupEvents() {
        btnBackSearch.setOnClickListener(v -> dismiss());

        ivClearSearchText.setOnClickListener(v -> {
            etSearchInput.setText("");
            clearResults();
        });

        adapter.setOnItemClickListener(item -> {
            if (item == null || item.chatId == null || item.chatId.isEmpty()) return;
            Intent intent = new Intent(activity, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, item.chatId);
            intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, item.chatType);
            intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, item.name != null ? item.name : "");
            intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, item.avatarUrl != null ? item.avatarUrl : "");
            activity.startActivity(intent);
            dismiss();
        });

        etSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                String query = etSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    executeSearch(query);
                }
                return true;
            }
            return false;
        });

        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String keyword = s != null ? s.toString().trim() : "";
                ivClearSearchText.setVisibility(keyword.isEmpty() ? View.GONE : View.VISIBLE);

                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                if (keyword.isEmpty()) {
                    clearResults();
                } else {
                    searchRunnable = () -> executeSearch(keyword);
                    searchHandler.postDelayed(searchRunnable, 280);
                }
            }
        });
    }

    private void clearResults() {
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
        pbSearchLoading.setVisibility(View.GONE);
        viewSearchDivider.setVisibility(View.GONE);
        rvSearchResults.setVisibility(View.GONE);
        layoutSearchEmpty.setVisibility(View.GONE);
        adapter.clear();
    }

    private void executeSearch(String keyword) {
        if (keyword.isEmpty()) {
            clearResults();
            return;
        }

        String token = PrefUtils.getToken(activity);
        if (token == null || token.isEmpty()) return;

        if (currentCall != null) {
            currentCall.cancel();
        }

        pbSearchLoading.setVisibility(View.VISIBLE);

        HashMap<String, String> params = new HashMap<>();
        params.put("word", keyword);
        String json = ApiClient.getGson().toJson(params);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), json);

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/search/home-search")
                .header("token", token)
                .post(body)
                .build();

        currentCall = ApiClient.getClient().newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                activity.runOnUiThread(() -> {
                    pbSearchLoading.setVisibility(View.GONE);
                    viewSearchDivider.setVisibility(View.VISIBLE);
                    rvSearchResults.setVisibility(View.GONE);
                    layoutSearchEmpty.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (call.isCanceled()) return;
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String respStr = response.body().string();
                        final List<ConversationSearchResultAdapter.Item> results = parseSearchResults(respStr);
                        activity.runOnUiThread(() -> {
                            pbSearchLoading.setVisibility(View.GONE);
                            viewSearchDivider.setVisibility(View.VISIBLE);
                            if (results.isEmpty()) {
                                rvSearchResults.setVisibility(View.GONE);
                                layoutSearchEmpty.setVisibility(View.VISIBLE);
                            } else {
                                layoutSearchEmpty.setVisibility(View.GONE);
                                rvSearchResults.setVisibility(View.VISIBLE);
                                adapter.setData(results);
                            }
                        });
                    } else {
                        activity.runOnUiThread(() -> {
                            pbSearchLoading.setVisibility(View.GONE);
                            viewSearchDivider.setVisibility(View.VISIBLE);
                            rvSearchResults.setVisibility(View.GONE);
                            layoutSearchEmpty.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "parse error", e);
                    activity.runOnUiThread(() -> {
                        pbSearchLoading.setVisibility(View.GONE);
                        viewSearchDivider.setVisibility(View.VISIBLE);
                        rvSearchResults.setVisibility(View.GONE);
                        layoutSearchEmpty.setVisibility(View.VISIBLE);
                    });
                }
            }
        });
    }

    private List<ConversationSearchResultAdapter.Item> parseSearchResults(String json) {
        List<ConversationSearchResultAdapter.Item> results = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("data")) return results;
        JsonObject data = root.getAsJsonObject("data");
        if (!data.has("list")) return results;
        JsonArray categories = data.getAsJsonArray("list");

        for (JsonElement catElem : categories) {
            JsonObject cat = catElem.getAsJsonObject();
            if (!cat.has("list") || cat.get("list").isJsonNull()) continue;
            JsonArray items = cat.getAsJsonArray("list");
            if (items.isEmpty()) continue;

            String title = getJsonString(cat, "title");
            results.add(ConversationSearchResultAdapter.Item.header(title + " (" + items.size() + ")"));

            for (JsonElement itemElem : items) {
                JsonObject item = itemElem.getAsJsonObject();
                String friendId = getJsonString(item, "friendId");
                int friendType = getJsonInt(item, "friendType", 1);
                String nickname = getJsonString(item, "nickname");
                String name = getJsonString(item, "name");
                String avatarUrl = getJsonString(item, "avatarUrl");

                String displayName = !nickname.isEmpty() ? nickname : name;
                String subtitle = (friendType == 2) ? "群聊" : ((friendType == 3) ? "机器人" : "联系人");

                results.add(ConversationSearchResultAdapter.Item.result(
                        friendId, friendType, displayName, avatarUrl, subtitle
                ));
            }
        }
        return results;
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private int getJsonInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return def;
    }

    private void hideKeyboard() {
        if (etSearchInput != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etSearchInput.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void show() {
        super.show();
        if (etSearchInput != null) {
            etSearchInput.postDelayed(() -> {
                etSearchInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        }
    }

    @Override
    public void dismiss() {
        hideKeyboard();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
        super.dismiss();
    }
}
