package com.ticket.booking.exception;

/** 같은 회원에게 이미 진행 중인 결제 대기 주문이 있다. */
public final class PendingOrderAlreadyExistsException extends BookingException {
    private static final String MESSAGE = "이미 진행 중인 결제 대기 주문이 있습니다.";

    public PendingOrderAlreadyExistsException() {
        super(BookingErrorCode.E5004, MESSAGE, null);
    }
}
