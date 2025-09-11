package com.flying.whitefox.data.model.music;

public interface ResultCallback<T> {
    void onSuccess(T result);
    void onError(String errorMessage,Exception error);
}
