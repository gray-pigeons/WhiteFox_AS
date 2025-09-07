package com.flying.whitefox.data.network;

public interface DataCallback<T> {
    void onSuccess(T data);
    void onError(String error);
}
