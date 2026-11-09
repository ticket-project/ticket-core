package com.ticket.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;

@SuppressWarnings("NonAsciiCharacters")
class TicketExceptionStackTraceTest {
    @Test
    void 업무_예외는_스택_트레이스를_만들지_않는다() {
        assertThat(new BookingException(BookingErrorCode.E3003).getStackTrace()).isEmpty();
    }

    @Test
    void 내부_오류는_스택_트레이스를_남긴다() {
        assertThat(new InternalErrorException().getStackTrace()).isNotEmpty();
    }

    @Test
    void 스택이_없어도_원인은_보관한다() {
        final IllegalStateException cause = new IllegalStateException("boom");

        assertThat(new AdmissionTokenException(AdmissionErrorCode.E8000, "reason", cause))
                .hasCause(cause);
    }
}
