package com.ticket.booking.internal.exception.handler;

import com.ticket.booking.internal.exception.BookingException;
import com.ticket.booking.internal.exception.ExceedHoldLimitException;
import com.ticket.booking.internal.exception.HoldBusyException;
import com.ticket.booking.internal.exception.NoAvailableSeatException;
import com.ticket.booking.internal.exception.NotYetReserveTimeException;
import com.ticket.booking.internal.exception.OrderNotOwnedException;
import com.ticket.booking.internal.exception.OrderNotPendingException;
import com.ticket.booking.internal.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.internal.exception.PerformanceIsPastException;
import com.ticket.booking.internal.exception.SeatAlreadyHoldException;
import com.ticket.booking.internal.exception.SeatAlreadySelectedException;
import com.ticket.booking.internal.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.internal.exception.SeatNotOwnedException;
import com.ticket.shared.ApiResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking 오류의 외부 계약(HTTP 상태, E-code, 공개 메시지)을 한곳에 고정한다.
 *
 * <p>예전에는 전역 advice 테스트가 booking의 E6000을 빌려 와 확인했다. 오류를 module이 소유하게
 * 되면서 그 자리를 여기가 대신한다 — {@code gatling-test}가 E4001·E6000·E6003을 하드코딩하므로
 * 이 표가 곧 외부 계약이다.
 */
@SuppressWarnings("NonAsciiCharacters")
class BookingExceptionHandlerTest {

    private final BookingExceptionHandler handler = new BookingExceptionHandler();

    static Stream<Arguments> 오류_계약() {
        return Stream.of(
                Arguments.of(new PerformanceIsPastException(), HttpStatus.BAD_REQUEST, "E3001",
                        "과거 공연은 예매할 수 없습니다."),
                Arguments.of(new NotYetReserveTimeException(), HttpStatus.BAD_REQUEST, "E3002",
                        "아직 예매가 오픈되지 않았습니다."),
                Arguments.of(new NoAvailableSeatException(), HttpStatus.BAD_REQUEST, "E3003",
                        "이용 가능한 좌석이 없습니다."),
                Arguments.of(new SeatMismatchInPerformanceException(), HttpStatus.BAD_REQUEST, "E4000",
                        "요청한 좌석 정보와 일치하지 않습니다."),
                Arguments.of(new SeatAlreadySelectedException(), HttpStatus.CONFLICT, "E4001",
                        "이미 선택된 좌석입니다."),
                Arguments.of(new SeatNotOwnedException(), HttpStatus.FORBIDDEN, "E4002",
                        "본인이 선택한 좌석만 해제할 수 있습니다."),
                Arguments.of(new OrderNotPendingException(), HttpStatus.CONFLICT, "E5002",
                        "결제 대기 주문만 처리할 수 있습니다."),
                Arguments.of(new OrderNotOwnedException(), HttpStatus.FORBIDDEN, "E5003",
                        "본인 주문만 처리할 수 있습니다."),
                Arguments.of(new PendingOrderAlreadyExistsException(), HttpStatus.CONFLICT, "E5004",
                        "이미 진행 중인 결제 대기 주문이 있습니다."),
                Arguments.of(new SeatAlreadyHoldException(), HttpStatus.CONFLICT, "E6000",
                        "좌석이 이미 선점되었습니다."),
                Arguments.of(new ExceedHoldLimitException(), HttpStatus.CONFLICT, "E6001",
                        "선점 가능한 좌석 수를 초과하였습니다."),
                Arguments.of(new HoldBusyException(), HttpStatus.CONFLICT, "E6003",
                        "좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요."));
    }

    @ParameterizedTest
    @MethodSource("오류_계약")
    void booking_오류는_정해진_상태와_E_code와_메시지로_응답한다(
            final BookingException exception,
            final HttpStatus expectedStatus,
            final String expectedCode,
            final String expectedMessage
    ) {
        final ResponseEntity<ApiResponse<Object>> response = handler.handleBookingException(exception);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(expectedCode);
        assertThat(response.getBody().getError().getMessage()).isEqualTo(expectedMessage);
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void 예외의_data는_error_data로_나가고_message를_덮지_않는다() {
        final ResponseEntity<ApiResponse<Object>> response =
                handler.handleBookingException(new HoldBusyException("좌석 처리 중입니다."));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getMessage())
                .isEqualTo("좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요.");
        assertThat(response.getBody().getError().getData()).isEqualTo("좌석 처리 중입니다.");
    }
}
