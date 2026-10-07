package com.ticket.shared.exception;

/**
 * 처리 중 예상하지 못한 실패다.
 *
 * <p>공개 메시지에 내부 사정을 담지 않는다 — 원인은 로그에만 남긴다. HTTP 500 매핑은
 * {@code com.ticket.shared.exception.handler.GlobalExceptionHandler}가 안다.
 */
public final class InternalErrorException extends TicketException {
    private static final String MESSAGE = "일시적인 오류가 발생했습니다.";

    public InternalErrorException() {
        super(CommonErrorCode.E500, MESSAGE, null);
    }
}
