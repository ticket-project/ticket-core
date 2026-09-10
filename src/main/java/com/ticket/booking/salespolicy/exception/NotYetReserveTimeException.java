package com.ticket.booking.salespolicy.exception;



import com.ticket.booking.support.exception.BookingErrorCode;
import com.ticket.booking.support.exception.BookingException;
import lombok.Getter;
/**
 * 아직 예매 오픈 시각 전이다.
 *
 * <p>{@code performanceId}는 진단 정보다. 공개 {@code error.data}에는 싣지 않는다.
 */
@Getter
public class NotYetReserveTimeException extends BookingException {

    private static final String MESSAGE = "아직 예매가 오픈되지 않았습니다.";

    private final Long performanceId;

    public NotYetReserveTimeException(final Long performanceId) {
        super(BookingErrorCode.E3002, MESSAGE, null);
        this.performanceId = performanceId;
    }
}
