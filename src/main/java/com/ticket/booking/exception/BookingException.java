package com.ticket.booking.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.ErrorCode;
import com.ticket.shared.exception.TicketException;

/**
 * booking 업무 오류의 base 타입이다. 이 module의 handler는 이 타입 하나만 잡는다. HTTP 상태는 예외가 아니라 handler가 안다 —
 * BookingExceptionHandler가 구체 타입별로 정한다.
 */
public abstract sealed class BookingException extends TicketException
        permits PerformanceIsPastException,
                BookingNotOpenYetException,
                NoAvailableSeatException,
                SeatMismatchInPerformanceException,
                SeatAlreadySelectedException,
                SeatNotOwnedException,
                SeatVenueMismatchException,
                PerformanceGradeMismatchException,
                PerformanceSeatAlreadyExistsException,
                OrderNotPendingException,
                OrderNotOwnedException,
                PendingOrderAlreadyExistsException,
                SeatAlreadyHeldException,
                HoldLimitExceededException,
                HoldBusyException {
    protected BookingException(
            final ErrorCode errorCode, final String message, final @Nullable Object data) {
        super(errorCode, message, data);
    }
}
