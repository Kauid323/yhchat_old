package com.nago8.chat.old.model;

import com.nago8.chat.old.proto.user.address_book_list;

public class AddressBookItem {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    private int viewType;
    private String headerTitle;
    private address_book_list.Data.Data_list data;
    private int chatType; // 1: friend, 2: group, 3: bot

    public AddressBookItem(String headerTitle) {
        this.viewType = TYPE_HEADER;
        this.headerTitle = headerTitle;
    }

    public AddressBookItem(address_book_list.Data.Data_list data, int chatType) {
        this.viewType = TYPE_ITEM;
        this.data = data;
        this.chatType = chatType;
    }

    public int getViewType() {
        return viewType;
    }

    public String getHeaderTitle() {
        return headerTitle;
    }

    public address_book_list.Data.Data_list getData() {
        return data;
    }

    public int getChatType() {
        return chatType;
    }

    public String getDisplayName() {
        if (data == null) return "";
        if (data.remark != null && !data.remark.trim().isEmpty()) {
            return data.remark;
        }
        if (data.name != null && !data.name.trim().isEmpty()) {
            return data.name;
        }
        return data.chat_id != null ? data.chat_id : "";
    }
}
