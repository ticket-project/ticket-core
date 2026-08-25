package com.ticket.core.support.response;

import com.ticket.support.error.ErrorMessage;
import com.ticket.support.error.ErrorType;

public class ApiResponse<T> {

    private final ResultType result;
    private final T data;
    private final ErrorMessage error;

    private ApiResponse(final ResultType result, final T data, final ErrorMessage error) {
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

    public static <S> ApiResponse<S> error(final ErrorType errorType) {
        return error(errorType, null);
    }

    public static <S> ApiResponse<S> error(final ErrorType errorType, final Object data) {
        return new ApiResponse<>(ResultType.ERROR, null, new ErrorMessage(errorType, data));
    }

    public ResultType getResult() {
        return result;
    }

    public T getData() {
        return data;
    }

    public ErrorMessage getError() {
        return error;
    }
}
