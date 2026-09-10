package com.ticket.error;

/**
 * 처리 중 예상하지 못한 실패다.
 *
 * <p>공개 메시지에 내부 사정을 담지 않는다 — 원인은 로그에만 남긴다. HTTP 500 매핑은
 * {@code com.ticket.error.handler.GlobalExceptionHandler}가 안다.
 */
public class InternalErrorException extends TicketException {

    private static final String MESSAGE = "일시적인 오류가 발생했습니다.";

    public InternalErrorException() {
        this(null);
    }

    /**
     * {@code data}에 무엇이 실패했는지 좁히는 문구를 싣는다. 내부 예외 메시지나 스택은 넣지 않는다.
     */
    public InternalErrorException(final Object data) {
        super(CommonErrorCode.E500, MESSAGE, data);
    }
}
