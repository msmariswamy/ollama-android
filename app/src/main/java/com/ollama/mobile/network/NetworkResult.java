package com.ollama.mobile.network;

public class NetworkResult<T> {
    public enum Status { SUCCESS, ERROR, LOADING }

    public final Status status;
    public final T data;
    public final String error;

    private NetworkResult(Status status, T data, String error) {
        this.status = status;
        this.data = data;
        this.error = error;
    }

    public static <T> NetworkResult<T> success(T data) {
        return new NetworkResult<>(Status.SUCCESS, data, null);
    }

    public static <T> NetworkResult<T> error(String msg) {
        return new NetworkResult<>(Status.ERROR, null, msg);
    }

    public static <T> NetworkResult<T> loading() {
        return new NetworkResult<>(Status.LOADING, null, null);
    }
}
