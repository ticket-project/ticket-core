package com.ticket.core.api.error;

/**
 * HTTP 오류 응답 본문이다. 응답 형식은 API가 소유하므로 support:error에 두지 않는다.
 *
 * <p>클라이언트는 message가 아니라 code로 분기한다.
 */
public class ApiErrorResponse {

    private final String code;
    private final String message;
    private final Object data;

    public ApiErrorResponse(final String code, final String message, final Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
