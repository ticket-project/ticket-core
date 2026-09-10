package com.ticket.error;

/**
 * 요청한 데이터가 없다. 어느 module에서든 뜻이 같아 여기 있다.
 *
 * <p>"어떤" 데이터가 없는지가 업무적으로 구분돼야 하면 그 module이 자기 예외를 갖는다. HTTP 404
 * 매핑은 {@code com.ticket.error.handler.GlobalExceptionHandler}가 안다.
 */
public class NotFoundException extends TicketException {

    private static final String MESSAGE = "요청하신 정보를 찾을 수 없습니다.";

    public NotFoundException() {
        this(null);
    }

    public NotFoundException(final Object data) {
        super(CommonErrorCode.E404, MESSAGE, data);
    }
}
