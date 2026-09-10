package com.ticket.booking.seat.exception;



import com.ticket.booking.support.exception.BookingErrorCode;
import com.ticket.booking.support.exception.BookingException;
import lombok.Getter;
/**
 * 이미 판매 좌석으로 편성된 (performance, seat) 조합을 다시 편성하려 했다.
 *
 * <p>{@code performanceId}는 진단 정보다. 이미 편성된 좌석이 무엇인지 찾기 위한 추가 조회는
 * 하지 않는다.
 */
@Getter
public class PerformanceSeatAlreadyEditionedException extends BookingException {

    private static final String MESSAGE = "이미 편성된 좌석입니다.";

    private final Long performanceId;

    public PerformanceSeatAlreadyEditionedException(final Long performanceId) {
        super(BookingErrorCode.E4005, MESSAGE, null);
        this.performanceId = performanceId;
    }
}
