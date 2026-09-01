package com.ticket.core.support.response;

import com.ticket.core.api.error.ApiErrorResponse;
import com.ticket.support.error.ErrorDefinition;

public class ApiResponse<T> {

    private final ResultType result;
    private final T data;
    private final ApiErrorResponse error;

    private ApiResponse(final ResultType result, final T data, final ApiErrorResponse error) {
        this.result = result;
        this.data = data;
        this.error = error;
    }

    public static <S> ApiResponse<S> success() {
        return new ApiResponse<>(ResultType.SUCCESS, null, null);
    }

    public static <S> ApiResponse<S> success(S data) {
        return new ApiResponse<>(ResultType.SUCCESS, data, null);
    }

    public static <S> ApiResponse<S> error(final ErrorDefinition errorType) {
        return error(errorType, null);
    }

    public static <S> ApiResponse<S> error(final ErrorDefinition errorType, final Object data) {
        return new ApiResponse<>(ResultType.ERROR, null, new ApiErrorResponse(errorType.getErrorCode().getCode(), errorType.getMessage(), data));
    }

    public ResultType getResult() {
        return result;
    }

    public T getData() {
        return data;
    }

    public ApiErrorResponse getError() {
        return error;
    }
}
