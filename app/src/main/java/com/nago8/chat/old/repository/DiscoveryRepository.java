package com.nago8.chat.old.repository;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.model.DiscoveryModels;
import com.nago8.chat.old.net.ApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DiscoveryRepository {

    private static final String BASE = ApiClient.BASE_URL;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface CallbackResult<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    /**
     * 获取机器人商店 Banner (POST /v1/bot/banner)
     */
    public Call getBanners(String token, CallbackResult<List<DiscoveryModels.BannerItem>> cb) {
        Request req = new Request.Builder()
                .url(BASE + "/v1/bot/banner")
                .header("token", token != null ? token : "")
                .post(RequestBody.create(JSON, "{}"))
                .build();

        Call call = ApiClient.getClient().newCall(req);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (cb != null) cb.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (cb != null) cb.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body().string();
                    JsonObject root = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<DiscoveryModels.BannerItem> list = new ArrayList<>();
                    if (root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        if (data.has("banners") && data.get("banners").isJsonArray()) {
                            JsonArray array = data.getAsJsonArray("banners");
                            for (JsonElement el : array) {
                                if (!el.isJsonObject()) continue;
                                JsonObject obj = el.getAsJsonObject();
                                DiscoveryModels.BannerItem item = new DiscoveryModels.BannerItem();
                                item.id = obj.has("id") && !obj.get("id").isJsonNull() ? obj.get("id").getAsInt() : 0;
                                item.title = obj.has("title") && !obj.get("title").isJsonNull() ? obj.get("title").getAsString() : "";
                                item.introduction = obj.has("introduction") && !obj.get("introduction").isJsonNull() ? obj.get("introduction").getAsString() : "";
                                item.targetId = obj.has("targetId") && !obj.get("targetId").isJsonNull() ? obj.get("targetId").getAsString() : "";
                                item.targetUrl = obj.has("targetUrl") && !obj.get("targetUrl").isJsonNull() ? obj.get("targetUrl").getAsString() : "";
                                item.imageUrl = obj.has("imageUrl") && !obj.get("imageUrl").isJsonNull() ? obj.get("imageUrl").getAsString() : "";
                                item.sort = obj.has("sort") && !obj.get("sort").isJsonNull() ? obj.get("sort").getAsInt() : 0;
                                item.typ = obj.has("typ") && !obj.get("typ").isJsonNull() ? obj.get("typ").getAsInt() : 0;
                                list.add(item);
                            }
                        }
                    }
                    if (cb != null) cb.onSuccess(list);
                } catch (Exception e) {
                    if (cb != null) cb.onError(e);
                } finally {
                    response.close();
                }
            }
        });
        return call;
    }

    /**
     * 获取推荐机器人列表 (POST /v1/user/recommend 或 POST /v1/bot/new-list)
     */
    public Call getRecommendedBots(String token, CallbackResult<List<DiscoveryModels.BotItem>> cb) {
        Request req = new Request.Builder()
                .url(BASE + "/v1/user/recommend")
                .header("token", token != null ? token : "")
                .post(RequestBody.create(JSON, "{}"))
                .build();

        Call call = ApiClient.getClient().newCall(req);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // 如果 /v1/user/recommend 失败，回退到 /v1/bot/new-list
                fallbackBotNewList(token, cb);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    fallbackBotNewList(token, cb);
                    return;
                }
                try {
                    String bodyStr = response.body().string();
                    JsonObject root = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<DiscoveryModels.BotItem> list = new ArrayList<>();
                    if (root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        JsonArray array = null;
                        if (data.has("botList") && data.get("botList").isJsonArray()) {
                            array = data.getAsJsonArray("botList");
                        } else if (data.has("bots") && data.get("bots").isJsonArray()) {
                            array = data.getAsJsonArray("bots");
                        }
                        if (array != null) {
                            for (JsonElement el : array) {
                                if (!el.isJsonObject()) continue;
                                JsonObject obj = el.getAsJsonObject();
                                DiscoveryModels.BotItem item = new DiscoveryModels.BotItem();
                                item.chatId = obj.has("chatId") && !obj.get("chatId").isJsonNull() ? obj.get("chatId").getAsString() : "";
                                item.chatType = 3;
                                item.headcount = obj.has("headcount") && !obj.get("headcount").isJsonNull() ? obj.get("headcount").getAsString() : "0";
                                item.nickname = obj.has("nickname") && !obj.get("nickname").isJsonNull() ? obj.get("nickname").getAsString() : "";
                                item.introduction = obj.has("introduction") && !obj.get("introduction").isJsonNull() ? obj.get("introduction").getAsString() : "";
                                item.instructions = obj.has("instructions") && !obj.get("instructions").isJsonNull() ? obj.get("instructions").getAsString() : "";
                                item.avatarUrl = obj.has("avatarUrl") && !obj.get("avatarUrl").isJsonNull() ? obj.get("avatarUrl").getAsString() : "";
                                list.add(item);
                            }
                        }
                    }
                    if (!list.isEmpty()) {
                        if (cb != null) cb.onSuccess(list);
                    } else {
                        fallbackBotNewList(token, cb);
                    }
                } catch (Exception e) {
                    fallbackBotNewList(token, cb);
                } finally {
                    response.close();
                }
            }
        });
        return call;
    }

    private void fallbackBotNewList(String token, CallbackResult<List<DiscoveryModels.BotItem>> cb) {
        Request req = new Request.Builder()
                .url(BASE + "/v1/bot/new-list")
                .header("token", token != null ? token : "")
                .post(RequestBody.create(JSON, "{}"))
                .build();

        ApiClient.getClient().newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (cb != null) cb.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (cb != null) cb.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body().string();
                    JsonObject root = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<DiscoveryModels.BotItem> list = new ArrayList<>();
                    if (root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        if (data.has("bots") && data.get("bots").isJsonArray()) {
                            JsonArray array = data.getAsJsonArray("bots");
                            for (JsonElement el : array) {
                                if (!el.isJsonObject()) continue;
                                JsonObject obj = el.getAsJsonObject();
                                DiscoveryModels.BotItem item = new DiscoveryModels.BotItem();
                                item.chatId = obj.has("chatId") && !obj.get("chatId").isJsonNull() ? obj.get("chatId").getAsString() : "";
                                item.chatType = 3;
                                item.headcount = obj.has("headcount") && !obj.get("headcount").isJsonNull() ? obj.get("headcount").getAsString() : "0";
                                item.nickname = obj.has("nickname") && !obj.get("nickname").isJsonNull() ? obj.get("nickname").getAsString() : "";
                                item.introduction = obj.has("introduction") && !obj.get("introduction").isJsonNull() ? obj.get("introduction").getAsString() : "";
                                item.instructions = obj.has("instructions") && !obj.get("instructions").isJsonNull() ? obj.get("instructions").getAsString() : "";
                                item.avatarUrl = obj.has("avatarUrl") && !obj.get("avatarUrl").isJsonNull() ? obj.get("avatarUrl").getAsString() : "";
                                list.add(item);
                            }
                        }
                    }
                    if (cb != null) cb.onSuccess(list);
                } catch (Exception e) {
                    if (cb != null) cb.onError(e);
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 获取发现群聊分类列表 (POST /v1/user/recommend-category-list)
     */
    public Call getRecommendCategoryList(String token, CallbackResult<List<String>> cb) {
        Request req = new Request.Builder()
                .url(BASE + "/v1/user/recommend-category-list")
                .header("token", token != null ? token : "")
                .post(RequestBody.create(JSON, "{}"))
                .build();

        Call call = ApiClient.getClient().newCall(req);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (cb != null) cb.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (cb != null) cb.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body().string();
                    JsonObject root = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<String> list = new ArrayList<>();
                    if (root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        if (data.has("categories") && data.get("categories").isJsonArray()) {
                            JsonArray array = data.getAsJsonArray("categories");
                            for (JsonElement el : array) {
                                if (el.isJsonPrimitive()) {
                                    list.add(el.getAsString());
                                }
                            }
                        }
                    }
                    if (cb != null) cb.onSuccess(list);
                } catch (Exception e) {
                    if (cb != null) cb.onError(e);
                } finally {
                    response.close();
                }
            }
        });
        return call;
    }

    /**
     * 获取发现推荐群聊列表 (POST /v1/user/recommend-list)
     */
    public Call getRecommendGroups(String token, String category, String keyword, int size, int page, CallbackResult<List<DiscoveryModels.GroupItem>> cb) {
        JsonObject reqObj = new JsonObject();
        reqObj.addProperty("category", category != null && !"全部".equals(category) && !"最新".equals(category) ? category : "");
        reqObj.addProperty("keyword", keyword != null ? keyword : "");
        reqObj.addProperty("size", size > 0 ? size : 30);
        reqObj.addProperty("page", page > 0 ? page : 1);

        Request req = new Request.Builder()
                .url(BASE + "/v1/user/recommend-list")
                .header("token", token != null ? token : "")
                .post(RequestBody.create(JSON, reqObj.toString()))
                .build();

        Call call = ApiClient.getClient().newCall(req);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (cb != null) cb.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (cb != null) cb.onError(new IOException("HTTP " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body().string();
                    JsonObject root = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<DiscoveryModels.GroupItem> list = new ArrayList<>();
                    if (root.has("data") && root.get("data").isJsonObject()) {
                        JsonObject data = root.getAsJsonObject("data");
                        JsonArray array = null;
                        if (data.has("groupList") && data.get("groupList").isJsonArray()) {
                            array = data.getAsJsonArray("groupList");
                        } else if (data.has("groups") && data.get("groups").isJsonArray()) {
                            array = data.getAsJsonArray("groups");
                        }
                        if (array != null) {
                            for (JsonElement el : array) {
                                if (!el.isJsonObject()) continue;
                                JsonObject obj = el.getAsJsonObject();
                                DiscoveryModels.GroupItem item = new DiscoveryModels.GroupItem();
                                item.groupId = obj.has("chatId") && !obj.get("chatId").isJsonNull() ? obj.get("chatId").getAsString() :
                                        (obj.has("groupId") && !obj.get("groupId").isJsonNull() ? obj.get("groupId").getAsString() : "");
                                item.name = obj.has("nickname") && !obj.get("nickname").isJsonNull() ? obj.get("nickname").getAsString() :
                                        (obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "");
                                item.introduction = obj.has("introduction") && !obj.get("introduction").isJsonNull() ? obj.get("introduction").getAsString() : "";
                                item.avatarUrl = obj.has("avatarUrl") && !obj.get("avatarUrl").isJsonNull() ? obj.get("avatarUrl").getAsString() : "";
                                item.headcount = obj.has("headcount") && !obj.get("headcount").isJsonNull() ? obj.get("headcount").getAsInt() : 0;
                                item.category = obj.has("category") && !obj.get("category").isJsonNull() ? obj.get("category").getAsString() : "";
                                list.add(item);
                            }
                        }
                    }
                    if (cb != null) cb.onSuccess(list);
                } catch (Exception e) {
                    if (cb != null) cb.onError(e);
                } finally {
                    response.close();
                }
            }
        });
        return call;
    }
}
