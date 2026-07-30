package com.nago8.chat.old.cache;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.user.ChatType;
import com.nago8.chat.old.proto.user.address_book_list;

import java.util.ArrayList;
import java.util.List;

public class AddressBookCache {
    private static final String TAG = "AddressBookCache";
    private static final String PREF_ADDRESS_BOOK = "pref_address_book";
    private static final String KEY_CACHED_DATA_JSON = "cached_data_json";

    public static void saveCache(Context context, List<address_book_list.Data> dataList) {
        if (context == null || dataList == null) return;
        try {
            List<SimpleCategory> simpleList = new ArrayList<>();
            for (address_book_list.Data categoryData : dataList) {
                if (categoryData == null || categoryData.data == null) continue;
                SimpleCategory category = new SimpleCategory();
                category.list_name = categoryData.list_name;
                category.chat_type_val = categoryData.chat_type != null ? categoryData.chat_type.getValue() : 1;
                category.items = new ArrayList<>();
                for (address_book_list.Data.Data_list item : categoryData.data) {
                    SimpleItem simpleItem = new SimpleItem();
                    simpleItem.chat_id = item.chat_id;
                    simpleItem.name = item.name;
                    simpleItem.remark = item.remark;
                    simpleItem.avatar_url = item.avatar_url;
                    simpleItem.permisson_level = item.permisson_level;
                    simpleItem.no_disturb = item.no_disturb;
                    category.items.add(simpleItem);
                }
                simpleList.add(category);
            }
            String json = ApiClient.getGson().toJson(simpleList);
            SharedPreferences sp = context.getSharedPreferences(PREF_ADDRESS_BOOK, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_CACHED_DATA_JSON, json).apply();
            Log.d(TAG, "Address book cache saved to disk.");
        } catch (Exception e) {
            Log.e(TAG, "saveCache failed", e);
        }
    }

    public static List<address_book_list.Data> loadCache(Context context) {
        if (context == null) return null;
        SharedPreferences sp = context.getSharedPreferences(PREF_ADDRESS_BOOK, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_CACHED_DATA_JSON, "");
        if (json == null || json.isEmpty()) return null;

        try {
            SimpleCategory[] categories = ApiClient.getGson().fromJson(json, SimpleCategory[].class);
            if (categories == null || categories.length == 0) return null;

            List<address_book_list.Data> protoDataList = new ArrayList<>();
            for (SimpleCategory cat : categories) {
                if (cat == null || cat.items == null) continue;
                ChatType cType = ChatType.fromValue(cat.chat_type_val);
                List<address_book_list.Data.Data_list> dataItems = new ArrayList<>();
                for (SimpleItem simpleItem : cat.items) {
                    address_book_list.Data.Data_list item = new address_book_list.Data.Data_list.Builder()
                            .chat_id(simpleItem.chat_id != null ? simpleItem.chat_id : "")
                            .name(simpleItem.name != null ? simpleItem.name : "")
                            .remark(simpleItem.remark != null ? simpleItem.remark : "")
                            .avatar_url(simpleItem.avatar_url != null ? simpleItem.avatar_url : "")
                            .permisson_level(simpleItem.permisson_level)
                            .no_disturb(simpleItem.no_disturb)
                            .build();
                    dataItems.add(item);
                }
                address_book_list.Data categoryData = new address_book_list.Data.Builder()
                        .list_name(cat.list_name != null ? cat.list_name : "")
                        .chat_type(cType != null ? cType : ChatType.user)
                        .data(dataItems)
                        .build();
                protoDataList.add(categoryData);
            }
            return protoDataList;
        } catch (Exception e) {
            Log.e(TAG, "loadCache failed", e);
        }
        return null;
    }

    public static void clearCache(Context context) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_ADDRESS_BOOK, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_CACHED_DATA_JSON).apply();
    }

    public static boolean containsUserId(Context context, String userId) {
        if (context == null || userId == null || userId.isEmpty()) return false;
        List<address_book_list.Data> dataList = loadCache(context);
        if (dataList == null) return false;
        for (address_book_list.Data category : dataList) {
            if (category == null || category.data == null) continue;
            for (address_book_list.Data.Data_list item : category.data) {
                if (item != null && userId.equals(item.chat_id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class SimpleCategory {
        String list_name;
        int chat_type_val;
        List<SimpleItem> items;
    }

    private static class SimpleItem {
        String chat_id;
        String name;
        String remark;
        String avatar_url;
        int permisson_level;
        boolean no_disturb;
    }
}
