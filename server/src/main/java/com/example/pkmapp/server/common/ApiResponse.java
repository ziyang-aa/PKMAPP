package com.example.pkmapp.server.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        boolean ok,
        T data,
        ApiError error
) {
    public record ApiError(String code, String message) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }
}
