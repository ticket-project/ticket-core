package com.ticket.booking.exception.handler;

import com.ticket.booking.exception.AdmissionTokenException;
import com.ticket.booking.exception.AdmissionTokenExpiredException;
import com.ticket.booking.exception.AdmissionTokenRequiredException;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.exception.ExceedHoldLimitException;
import com.ticket.booking.exception.HoldBusyException;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.NotYetReserveTimeException;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.exception.OrderNotOwnedException;
import com.ticket.booking.exception.OrderNotPendingException;
import com.ticket.booking.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.exception.PerformanceGradeMismatchException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.exception.PerformanceSeatAlreadyEditionedException;
import com.ticket.booking.exception.SeatAlreadyHoldException;
import com.ticket.booking.exception.SeatAlreadySelectedException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.exception.SeatNotOwnedException;
import com.ticket.booking.exception.SeatVenueMismatchException;
import com.ticket.shared.web.ApiResponse;
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
 *
 * <p>admission token 오류(E8xxx)도 이 handler가 잡으므로 여기서 함께 고정한다. 검증 실패
 * 사유({@code reason})는 응답에 노출되지 않고 로그에만 쓰인다.
 */
@SuppressWarnings("NonAsciiCharacters")
class BookingExceptionHandlerTest {

    private final BookingExceptionHandler handler = new BookingExceptionHandler();

    static Stream<Arguments> 오류_계약() {
        return Stream.of(
                Arguments.of(new PerformanceIsPastException(10L), HttpStatus.BAD_REQUEST, "E3001",
                        "과거 공연은 예매할 수 없습니다."),
                Arguments.of(new NotYetReserveTimeException(10L), HttpStatus.BAD_REQUEST, "E3002",
                        "아직 예매가 오픈되지 않았습니다."),
                Arguments.of(new NoAvailableSeatException(10L), HttpStatus.BAD_REQUEST, "E3003",
                        "이용 가능한 좌석이 없습니다."),
                Arguments.of(new SeatMismatchInPerformanceException(10L), HttpStatus.BAD_REQUEST, "E4000",
                        "요청한 좌석 정보와 일치하지 않습니다."),
                Arguments.of(new SeatAlreadySelectedException(10L, 20L), HttpStatus.CONFLICT, "E4001",
                        "이미 선택된 좌석입니다."),
                Arguments.of(new SeatNotOwnedException(10L, 20L, 30L), HttpStatus.FORBIDDEN, "E4002",
                        "본인이 선택한 좌석만 해제할 수 있습니다."),
                Arguments.of(new SeatVenueMismatchException(10L, 20L), HttpStatus.BAD_REQUEST, "E4003",
                        "요청한 좌석이 이 회차의 공연장에 속하지 않습니다."),
                Arguments.of(new PerformanceGradeMismatchException(10L, 40L), HttpStatus.BAD_REQUEST, "E4004",
                        "요청한 등급이 이 회차에 속하지 않습니다."),
                Arguments.of(new PerformanceSeatAlreadyEditionedException(10L), HttpStatus.BAD_REQUEST, "E4005",
                        "이미 편성된 좌석입니다."),
                Arguments.of(new OrderNotPendingException(OrderState.CANCELED), HttpStatus.CONFLICT, "E5002",
                        "결제 대기 주문만 처리할 수 있습니다."),
                Arguments.of(new OrderNotOwnedException("order-key", 30L), HttpStatus.FORBIDDEN, "E5003",
                        "본인 주문만 처리할 수 있습니다."),
                Arguments.of(new PendingOrderAlreadyExistsException(30L, 10L), HttpStatus.CONFLICT, "E5004",
                        "이미 진행 중인 결제 대기 주문이 있습니다."),
                Arguments.of(new SeatAlreadyHoldException(10L, 20L), HttpStatus.CONFLICT, "E6000",
                        "좌석이 이미 선점되었습니다."),
                Arguments.of(new ExceedHoldLimitException(5L, 4), HttpStatus.CONFLICT, "E6001",
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
        // 새로 생긴 진단 필드(performanceId, seatId, memberId, 수량)가 error.data로 새어 나가지
        // 않는지 여기서 본다 — 바깥 봉투의 getData()는 성공 data라 이것을 잡지 못한다.
        assertThat(response.getBody().getError().getData()).isNull();
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

    static Stream<Arguments> admission_오류_계약() {
        return Stream.of(
                Arguments.of(new AdmissionTokenRequiredException(), HttpStatus.FORBIDDEN, "E8000",
                        "대기열 입장 토큰이 필요합니다."),
                Arguments.of(new AdmissionTokenExpiredException("expired-reason", null), HttpStatus.FORBIDDEN, "E8001",
                        "대기열 입장 토큰이 만료되었습니다."),
                Arguments.of(new AdmissionTokenException("bad-signature"), HttpStatus.FORBIDDEN, "E8002",
                        "대기열 입장 토큰이 올바르지 않습니다."));
    }

    @ParameterizedTest
    @MethodSource("admission_오류_계약")
    void admission_오류는_정해진_상태와_E_code와_메시지로_응답한다(
            final AdmissionTokenException exception,
            final HttpStatus expectedStatus,
            final String expectedCode,
            final String expectedMessage
    ) {
        final ResponseEntity<ApiResponse<Object>> response = handler.handleAdmissionTokenException(exception);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(expectedCode);
        assertThat(response.getBody().getError().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void admission_검증_실패_사유는_응답에_노출되지_않는다() {
        final AdmissionTokenException exception = new AdmissionTokenException("서명 불일치: 상세 진단 정보");

        final ResponseEntity<ApiResponse<Object>> response = handler.handleAdmissionTokenException(exception);

        assertThat(exception.getReason()).isEqualTo("서명 불일치: 상세 진단 정보");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError().getMessage()).doesNotContain("서명 불일치");
    }
}
