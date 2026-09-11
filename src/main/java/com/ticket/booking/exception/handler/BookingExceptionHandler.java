package com.ticket.booking.exception.handler;

import com.ticket.booking.exception.AdmissionTokenException;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.exception.ExceedHoldLimitException;
import com.ticket.booking.exception.HoldBusyException;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.exception.NotYetReserveTimeException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * booking 오류를 응답으로 옮긴다. base 예외 하나만 잡는다 —
 * 그 범위는 {@code com.ticket.shared.exception.ExceptionHandlerScopeTest}가 강제한다.
 *
 * <p>{@link BookingException}은 상태를 모른다 — 구체 타입별 HTTP 상태는 이 handler가 안다.
 * {@code com.ticket.booking.exception.handler.BookingExceptionHandlerTest}가 15종 전부의
 * 상태·E-code·메시지를 고정한다({@code gatling-test}가 E4001·E6000·E6003 등을 하드코딩하는
 * 외부 계약이다).
 *
 * <p>admission token 검증 오류도 여기서 잡는다. {@link AdmissionTokenException}은
 * {@link BookingException}의 하위 타입이 아니므로(공통 base는 {@code TicketException}) 별도
 * 메서드를 둔다 — 검증 실패 사유를 admission의 어휘로 로깅하고 공개 메시지를 사유와 분리하는
 * 정책이 booking 일반 오류와 다르기 때문이다. 세 종류(required/expired/invalid) 모두 403이다.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BookingExceptionHandler {

    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ApiResponse<Object>> handleBookingException(final BookingException exception) {
        log.info("booking.rejected: code={}", exception.getErrorCode().getCode());

        return ResponseEntity
                .status(statusOf(exception))
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }

    @ExceptionHandler(AdmissionTokenException.class)
    public ResponseEntity<ApiResponse<Object>> handleAdmissionTokenException(
            final AdmissionTokenException exception
    ) {
        log.info("admission.rejected: code={}, reason={}", exception.getErrorCode().getCode(), exception.getReason());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN.value())
                .body(ApiResponse.error(
                        exception.getErrorCode().getCode(), exception.getMessage(), exception.getData()));
    }

    private HttpStatus statusOf(final BookingException exception) {
        return switch (exception) {
            case PerformanceIsPastException e -> HttpStatus.BAD_REQUEST;
            case NotYetReserveTimeException e -> HttpStatus.BAD_REQUEST;
            case NoAvailableSeatException e -> HttpStatus.BAD_REQUEST;
            case SeatMismatchInPerformanceException e -> HttpStatus.BAD_REQUEST;
            case SeatAlreadySelectedException e -> HttpStatus.CONFLICT;
            case SeatNotOwnedException e -> HttpStatus.FORBIDDEN;
            case SeatVenueMismatchException e -> HttpStatus.BAD_REQUEST;
            case PerformanceGradeMismatchException e -> HttpStatus.BAD_REQUEST;
            case PerformanceSeatAlreadyEditionedException e -> HttpStatus.BAD_REQUEST;
            case OrderNotPendingException e -> HttpStatus.CONFLICT;
            case OrderNotOwnedException e -> HttpStatus.FORBIDDEN;
            case PendingOrderAlreadyExistsException e -> HttpStatus.CONFLICT;
            case SeatAlreadyHoldException e -> HttpStatus.CONFLICT;
            case ExceedHoldLimitException e -> HttpStatus.CONFLICT;
            case HoldBusyException e -> HttpStatus.CONFLICT;
            default -> throw new IllegalStateException(
                    "알 수 없는 BookingException 하위 타입입니다: " + exception.getClass().getName());
        };
    }
}
