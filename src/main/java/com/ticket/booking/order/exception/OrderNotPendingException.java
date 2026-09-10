package com.ticket.booking.order.exception;



import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.support.exception.BookingErrorCode;
import com.ticket.booking.support.exception.BookingException;
import lombok.Getter;
/**
 * 결제 대기 상태가 아닌 주문을 처리하려 했다.
 *
 * <p>{@code currentStatus}는 거절 시점에 조회한 실제 주문 상태다. 진단 정보이고 공개
 * {@code error.data}에는 싣지 않는다.
 */
@Getter
public class OrderNotPendingException extends BookingException {

    private static final String MESSAGE = "결제 대기 주문만 처리할 수 있습니다.";

    private final OrderState currentStatus;

    public OrderNotPendingException(final OrderState currentStatus) {
        super(BookingErrorCode.E5002, MESSAGE, null);
        this.currentStatus = currentStatus;
    }
}
