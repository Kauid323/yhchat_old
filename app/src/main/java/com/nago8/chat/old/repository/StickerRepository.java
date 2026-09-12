package com.nago8.chat.old.repository;

import androidx.annotation.NonNull;

import com.nago8.chat.old.model.StickerItem;
import com.nago8.chat.old.model.StickerPack;
import com.nago8.chat.old.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class StickerRepository {

    public interface StickerPacksCallback {
        void onSuccess(List<StickerPack> packs);
        void onError(Exception error);
    }

    public interface StickerPackDetailCallback {
        void onSuccess(StickerPack pack);
        void onError(Exception error);
    }

    public interface CreatePackCallback {
        void onSuccess(long packId);
        void onError(Exception error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(Exception error);
    }

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * 获取用户收藏的表情包列表 (POST /v1/sticker/list)
     */
    public Call listStickerPacks(String token, StickerPacksCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        RequestBody body = RequestBody.create(JSON_TYPE, "{}");
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/sticker/list")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code != 1) {
                        String msg = root.optString("msg", "Failed to load stickers");
                        callback.onError(new Exception(msg));
                        return;
                    }

                    JSONObject data = root.optJSONObject("data");
                    List<StickerPack> packs = new ArrayList<>();
                    if (data != null) {
                        JSONArray packsArr = data.optJSONArray("stickerPacks");
                        if (packsArr == null) packsArr = data.optJSONArray("sticker_packs");
                        if (packsArr == null) packsArr = data.optJSONArray("list");
                        if (packsArr == null) packsArr = data.optJSONArray("packs");
                        if (packsArr != null) {
                            for (int i = 0; i < packsArr.length(); i++) {
                                JSONObject pObj = packsArr.optJSONObject(i);
                                if (pObj != null) {
                                    packs.add(parseStickerPack(pObj));
                                }
                            }
                        }
                    } else {
                        JSONArray rootArr = root.optJSONArray("data");
                        if (rootArr != null) {
                            for (int i = 0; i < rootArr.length(); i++) {
                                JSONObject pObj = rootArr.optJSONObject(i);
                                if (pObj != null) {
                                    packs.add(parseStickerPack(pObj));
                                }
                            }
                        }
                    }
                    callback.onSuccess(packs);
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }

    /**
     * 查看表情包详情 (POST /v1/sticker/detail)
     */
    public Call getStickerPackDetail(String token, long packId, StickerPackDetailCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("id", packId);
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(JSON_TYPE, reqJson.toString());
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/sticker/detail")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    android.util.Log.d("StickerRepository", "getStickerPackDetail response: " + jsonStr);
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code != 1) {
                        String msg = root.optString("msg", root.optString("message", "Failed to load sticker detail"));
                        callback.onError(new Exception(msg));
                        return;
                    }

                    JSONObject data = root.optJSONObject("data");
                    if (data == null) {
                        callback.onError(new Exception("No data in response"));
                        return;
                    }

                    JSONObject packObj = data.optJSONObject("stickerPack");
                    if (packObj == null) packObj = data.optJSONObject("sticker_pack");
                    if (packObj == null) packObj = data.optJSONObject("pack");
                    if (packObj == null && (data.has("id") || data.has("name") || data.has("stickerItems") || data.has("sticker_items") || data.has("items"))) {
                        packObj = data;
                    }
                    if (packObj == null) {
                        packObj = data;
                    }

                    StickerPack pack = parseStickerPack(packObj);
                    if (pack.stickerItems.isEmpty() && packObj != data) {
                        JSONArray fallbackItems = data.optJSONArray("stickerItems");
                        if (fallbackItems == null) fallbackItems = data.optJSONArray("sticker_items");
                        if (fallbackItems == null) fallbackItems = data.optJSONArray("items");
                        if (fallbackItems == null) fallbackItems = data.optJSONArray("stickers");
                        if (fallbackItems == null) fallbackItems = data.optJSONArray("list");
                        if (fallbackItems != null) {
                            for (int j = 0; j < fallbackItems.length(); j++) {
                                JSONObject iObj = fallbackItems.optJSONObject(j);
                                if (iObj != null) {
                                    StickerItem item = parseStickerItem(iObj, pack.id);
                                    if (item != null) pack.stickerItems.add(item);
                                } else {
                                    String strUrl = fallbackItems.optString(j, "");
                                    if (!strUrl.isEmpty()) {
                                        StickerItem item = new StickerItem(j + 1, "sticker", strUrl, pack.id);
                                        pack.stickerItems.add(item);
                                    }
                                }
                            }
                        }
                    }

                    JSONObject userObj = data.optJSONObject("user");
                    if (userObj == null) userObj = data.optJSONObject("creator");
                    if (userObj == null) userObj = packObj.optJSONObject("user");
                    if (userObj == null) userObj = packObj.optJSONObject("creator");
                    if (userObj != null) {
                        pack.creatorUserId = userObj.optString("user_id", userObj.optString("userId", userObj.optString("id", "")));
                        pack.creatorNickname = userObj.optString("nickname", userObj.optString("nick_name", userObj.optString("name", "")));
                        pack.creatorAvatarUrl = userObj.optString("avatar_url", userObj.optString("avatarUrl", userObj.optString("avatar", "")));
                    }

                    callback.onSuccess(pack);
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }

    /**
     * 添加/收藏表情包 (POST /v1/sticker/add)
     */
    public Call addStickerPack(String token, long packId, SimpleCallback callback) {
        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("id", packId);
        } catch (Exception ignored) {}
        return executeSimplePost(token, "/v1/sticker/add", reqJson.toString(), callback);
    }

    /**
     * 移除收藏表情包 (POST /v1/sticker/remove-sticker-pack)
     */
    public Call removeStickerPack(String token, long packId, SimpleCallback callback) {
        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("id", packId);
        } catch (Exception ignored) {}
        return executeSimplePost(token, "/v1/sticker/remove-sticker-pack", reqJson.toString(), callback);
    }

    /**
     * 更改收藏表情包的排序 (POST /v1/sticker/sort)
     */
    public Call sortStickerPacks(String token, List<StickerPack> packs, SimpleCallback callback) {
        try {
            JSONArray sortArr = new JSONArray();
            // 数字越大越靠前
            int total = packs.size();
            for (int i = 0; i < total; i++) {
                StickerPack p = packs.get(i);
                JSONObject item = new JSONObject();
                item.put("id", String.valueOf(p.id));
                item.put("sort", String.valueOf(total - i));
                sortArr.put(item);
            }

            JSONObject reqJson = new JSONObject();
            reqJson.put("sort", sortArr.toString());

            return executeSimplePost(token, "/v1/sticker/sort", reqJson.toString(), callback);
        } catch (Exception e) {
            callback.onError(e);
            return null;
        }
    }

    /**
     * 创建表情包 (POST /v1/sticker/create-pack)
     */
    public Call createStickerPack(String token, String name, CreatePackCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("name", name);
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(JSON_TYPE, reqJson.toString());
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/sticker/create-pack")
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code != 1) {
                        String msg = root.optString("msg", "Failed to create sticker pack");
                        callback.onError(new Exception(msg));
                        return;
                    }

                    JSONObject data = root.optJSONObject("data");
                    long id = data != null ? data.optLong("id", 0) : 0;
                    callback.onSuccess(id);
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }

    /**
     * 重命名表情包 (POST /v1/sticker/rename-pack)
     */
    public Call renameStickerPack(String token, long packId, String name, SimpleCallback callback) {
        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("id", packId);
            reqJson.put("name", name);
        } catch (Exception ignored) {}
        return executeSimplePost(token, "/v1/sticker/rename-pack", reqJson.toString(), callback);
    }

    /**
     * 删除表情包 (POST /v1/sticker/delete-pack)
     */
    public Call deleteStickerPack(String token, long packId, SimpleCallback callback) {
        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("id", packId);
        } catch (Exception ignored) {}
        return executeSimplePost(token, "/v1/sticker/delete-pack", reqJson.toString(), callback);
    }

    /**
     * 添加表情至表情包 (POST /v1/sticker/add-sticker)
     */
    public Call addStickerItem(String token, long packId, String name, String keyUrl, SimpleCallback callback) {
        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("stickerPackId", packId);
            reqJson.put("name", name != null ? name : "sticker");
            reqJson.put("url", keyUrl);
        } catch (Exception ignored) {}
        return executeSimplePost(token, "/v1/sticker/add-sticker", reqJson.toString(), callback);
    }

    /**
     * 重命名表情 (POST /v1/sticker/rename-sticker)
     */
    public Call renameStickerItem(String token, long itemId, String name, SimpleCallback callback) {
        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("id", itemId);
            reqJson.put("name", name);
        } catch (Exception ignored) {}
        return executeSimplePost(token, "/v1/sticker/rename-sticker", reqJson.toString(), callback);
    }

    /**
     * 删除表情 (POST /v1/sticker/remove-sticker)
     */
    public Call removeStickerItem(String token, long itemId, SimpleCallback callback) {
        JSONObject reqJson = new JSONObject();
        try {
            reqJson.put("id", itemId);
        } catch (Exception ignored) {}
        return executeSimplePost(token, "/v1/sticker/remove-sticker", reqJson.toString(), callback);
    }

    private Call executeSimplePost(String token, String path, String jsonPayload, SimpleCallback callback) {
        if (token == null || token.isEmpty()) {
            callback.onError(new IllegalArgumentException("token is empty"));
            return null;
        }

        RequestBody body = RequestBody.create(JSON_TYPE, jsonPayload);
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + path)
                .header("token", token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String jsonStr = response.body().string();
                    JSONObject root = new JSONObject(jsonStr);
                    int code = root.optInt("code", 0);
                    if (code == 1) {
                        callback.onSuccess();
                    } else {
                        String msg = root.optString("msg", "Operation failed");
                        callback.onError(new Exception(msg));
                    }
                } catch (Exception e) {
                    callback.onError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }

    private static StickerItem parseStickerItem(JSONObject iObj, long defaultPackId) {
        if (iObj == null) return null;
        StickerItem item = new StickerItem();
        item.id = iObj.optLong("id", iObj.optLong("item_id", iObj.optLong("sticker_id", iObj.optLong("stickerId", 0))));
        item.name = iObj.optString("name", iObj.optString("sticker_name", iObj.optString("title", "")));
        
        String url = iObj.optString("url", iObj.optString("image_url", iObj.optString("imageUrl", 
                iObj.optString("path", iObj.optString("img_url", iObj.optString("key", 
                iObj.optString("file_key", iObj.optString("pic_url", iObj.optString("cover_url", "")))))))));
        item.url = url;
        item.stickerPackId = iObj.optLong("stickerPackId", iObj.optLong("sticker_pack_id", iObj.optLong("pack_id", defaultPackId)));
        item.createBy = iObj.optString("createBy", iObj.optString("create_by", iObj.optString("user_id", "")));
        item.createTime = iObj.optLong("createTime", iObj.optLong("create_time", 0));
        item.delFlag = iObj.optInt("delFlag", iObj.optInt("del_flag", 0));
        return item;
    }

    private static StickerPack parseStickerPack(JSONObject pObj) {
        StickerPack pack = new StickerPack();
        pack.id = pObj.optLong("id", pObj.optLong("pack_id", pObj.optLong("stickerPackId", pObj.optLong("sticker_pack_id", 0))));
        pack.name = pObj.optString("name", pObj.optString("pack_name", ""));
        pack.createBy = pObj.optString("createBy", pObj.optString("create_by", pObj.optString("creator_id", pObj.optString("userId", pObj.optString("user_id", "")))));
        pack.createTime = pObj.optLong("createTime", pObj.optLong("create_time", 0));
        pack.delFlag = pObj.optInt("delFlag", pObj.optInt("del_flag", 0));
        pack.userCount = pObj.optInt("userCount", pObj.optInt("user_count", 0));
        pack.hot = pObj.optInt("hot", 0);
        pack.uuid = pObj.optString("uuid", "");
        pack.updateTime = pObj.optLong("updateTime", pObj.optLong("update_time", 0));
        pack.sort = pObj.optInt("sort", 0);

        JSONArray itemsArr = pObj.optJSONArray("stickerItems");
        if (itemsArr == null) itemsArr = pObj.optJSONArray("sticker_items");
        if (itemsArr == null) itemsArr = pObj.optJSONArray("items");
        if (itemsArr == null) itemsArr = pObj.optJSONArray("stickers");
        if (itemsArr == null) itemsArr = pObj.optJSONArray("list");
        if (itemsArr != null) {
            for (int j = 0; j < itemsArr.length(); j++) {
                JSONObject iObj = itemsArr.optJSONObject(j);
                if (iObj != null) {
                    StickerItem item = parseStickerItem(iObj, pack.id);
                    if (item != null) pack.stickerItems.add(item);
                } else {
                    String strUrl = itemsArr.optString(j, "");
                    if (!strUrl.isEmpty()) {
                        StickerItem item = new StickerItem(j + 1, "sticker", strUrl, pack.id);
                        pack.stickerItems.add(item);
                    }
                }
            }
        }
        return pack;
    }
}
