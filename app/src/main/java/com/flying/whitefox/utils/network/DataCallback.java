package com.flying.whitefox.utils.network;

public interface DataCallback<T> {
    void onSuccess(T data);
    void onError(String error);
}
