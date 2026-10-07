package com.ticket.booking.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.TicketException;

/**
 * booking 업무 오류다. 공개 문구는 {@link BookingErrorCode}가 갖고, HTTP 상태는 예외가 아니라 handler가 오류 코드로 정한다(BookingExceptionHandler).
 */
public final class BookingException extends TicketException {
    public BookingException(final BookingErrorCode errorCode) {
        this(errorCode, null);
    }

    /** @param data 어떤 경합인지 좁히는 <b>공개</b> 상세 문구다. 그대로 {@code error.data}로 나가고 고정 문구를 덮지 않는다. */
    public BookingException(final BookingErrorCode errorCode, final @Nullable Object data) {
        super(errorCode, errorCode.getMessage(), data);
    }

    @Override
    public BookingErrorCode getErrorCode() {
        return (BookingErrorCode) super.getErrorCode();
    }
}
