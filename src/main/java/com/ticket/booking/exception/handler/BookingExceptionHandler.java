package com.ticket.booking.exception.handler;

import com.ticket.booking.exception.BookingException;
import com.ticket.booking.salespolicy.exception.ExceedHoldLimitException;
import com.ticket.booking.exception.HoldBusyException;
import com.ticket.booking.exception.NoAvailableSeatException;
import com.ticket.booking.salespolicy.exception.NotYetReserveTimeException;
import com.ticket.booking.order.exception.OrderNotOwnedException;
import com.ticket.booking.order.exception.OrderNotPendingException;
import com.ticket.booking.order.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.seat.exception.PerformanceGradeMismatchException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.seat.exception.PerformanceSeatAlreadyEditionedException;
import com.ticket.booking.exception.SeatAlreadyHoldException;
import com.ticket.booking.selection.exception.SeatAlreadySelectedException;
import com.ticket.booking.exception.SeatMismatchInPerformanceException;
import com.ticket.booking.selection.exception.SeatNotOwnedException;
import com.ticket.booking.seat.exception.SeatVenueMismatchException;
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
