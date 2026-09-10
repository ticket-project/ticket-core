package com.ticket.error;

/**
 * 요청 자체가 올바르지 않다. 어느 module에서든 뜻이 같아 여기 있다.
 *
 * <p>{@code data}에는 어디가 잘못됐는지를 좁히는 문자열을 싣는다(필드 오류 목록 등).
 * {@code error.message}는 항상 고정 문구이므로 사유를 message에 넣지 않는다. HTTP 400 매핑은
 * {@code com.ticket.error.handler.GlobalExceptionHandler}가 안다.
 */
public class InvalidRequestException extends TicketException {

    private static final String MESSAGE = "요청이 올바르지 않습니다.";

    public InvalidRequestException() {
        this(null);
    }

    public InvalidRequestException(final Object data) {
        super(CommonErrorCode.E400, MESSAGE, data);
    }
}
