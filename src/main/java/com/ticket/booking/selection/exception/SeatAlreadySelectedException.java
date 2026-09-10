package com.ticket.booking.selection.exception;



import com.ticket.booking.support.exception.BookingErrorCode;
import com.ticket.booking.support.exception.BookingException;
import lombok.Getter;
/**
 * 다른 회원이 이미 선택 중인 좌석이다.
 *
 * <p>{@code performanceId}·{@code seatId}는 진단 정보다. 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public class SeatAlreadySelectedException extends BookingException {

    private static final String MESSAGE = "이미 선택된 좌석입니다.";

    private final Long performanceId;
    private final Long seatId;

    public SeatAlreadySelectedException(final Long performanceId, final Long seatId) {
        super(BookingErrorCode.E4001, MESSAGE, null);
        this.performanceId = performanceId;
        this.seatId = seatId;
    }
}
