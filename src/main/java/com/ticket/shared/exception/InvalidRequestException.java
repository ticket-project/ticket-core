package com.ticket.shared.exception;

/**
 * 요청 자체가 올바르지 않다. 어느 module에서든 뜻이 같아 여기 있다.
 *
 * <p>{@code data}에는 어디가 잘못됐는지를 좁히는 문자열을 싣는다(필드 오류 목록 등).
 * {@code error.message}는 항상 고정 문구이므로 사유를 message에 넣지 않는다. HTTP 400 매핑은
 * {@code com.ticket.shared.exception.handler.GlobalExceptionHandler}가 안다.
 */
public class InvalidRequestException extends TicketException {

    private static final String MESSAGE = "요청이 올바르지 않습니다.";

    public InvalidRequestException() {
        this(null);
    }

    /**
     * @param detail 어디가 잘못됐는지 좁히는 <b>공개</b> 상세 문구다. 그대로 {@code error.data}로
     *               나가고 고정 {@code message}를 덮지 않는다. 내부 사정은 넣지 않는다.
     */
    public InvalidRequestException(final String detail) {
        super(CommonErrorCode.E400, MESSAGE, detail);
    }
}
