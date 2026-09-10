package com.ticket.booking.order.exception;



import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import lombok.Getter;
/**
 * 본인 주문이 아닌 주문에 접근했다.
 *
 * <p>두 값 모두 <b>요청이 가져온</b> 값이다. 실제 주문 소유자는 담지 않는다 — 그 값을 위해
 * 저장소를 다시 조회하지 않고, 남의 소유 사실을 응답에 흘리지도 않는다. 진단 정보이고 공개
 * {@code error.data}에는 싣지 않는다.
 */
@Getter
public class OrderNotOwnedException extends BookingException {

    private static final String MESSAGE = "본인 주문만 처리할 수 있습니다.";

    private final String orderKey;
    private final Long memberId;

    public OrderNotOwnedException(final String orderKey, final Long memberId) {
        super(BookingErrorCode.E5003, MESSAGE, null);
        this.orderKey = orderKey;
        this.memberId = memberId;
    }
}
