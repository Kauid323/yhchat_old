package com.nago8.chat.old.repository;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.user.address_book_list;
import com.nago8.chat.old.proto.user.address_book_list_send;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FriendRepository {

    public interface AddressBookCallback {
        void onSuccess(address_book_list response);
        void onError(Exception error);
    }

    public Call getAddressBook(String token, String md5, AddressBookCallback callback) {
        address_book_list_send requestProto = new address_book_list_send.Builder()
                .md5(md5 == null ? "" : md5)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/friend/address-book-list")
                .header("token", token == null ? "" : token)
                .post(body)
                .build();

        Call call = ApiClient.getClient().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        if (callback != null) {
                            callback.onError(new IOException("HTTP " + response.code()));
                        }
                        return;
                    }
                    address_book_list result = address_book_list.ADAPTER.decode(response.body().source());
                    if (callback != null) {
                        callback.onSuccess(result);
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e);
                    }
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
        return call;
    }
}
