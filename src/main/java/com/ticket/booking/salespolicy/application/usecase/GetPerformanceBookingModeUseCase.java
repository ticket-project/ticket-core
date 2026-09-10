package com.ticket.booking.salespolicy.application.usecase;

import com.ticket.booking.salespolicy.domain.OrderAcceptanceStatus;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 인증 없이 회차의 예매 방식을 조회하는 booking 소유 use case다. 안내용 조회이므로 실제 좌석
 * 선택·상태·주문 API는 이 결과와 무관하게 실행 시점에 정책을 다시 검사한다. FE 라우트나
 * ticket-queue HTTP 경로는 담지 않는다 — {@code bookingMode}는 업무 의미만 전달한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceBookingModeUseCase {

    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
    private final Clock clock;

    public record Input(Long performanceId) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
        }
    }

    public enum BookingMode {
        DIRECT,
        QUEUE,
        UNAVAILABLE
    }

    public record Output(
            Long performanceId,
            OrderAcceptanceStatus acceptanceStatus,
            BookingMode bookingMode,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            Integer maxSeatCount,
            long holdDurationSeconds
    ) {
    }

    public Output execute(final Input input) {
        final PerformanceSalesPolicy policy = performanceSalesPolicyRepository.findById(input.performanceId())
                .orElseThrow(() -> new NotFoundException(
                        "회차 판매 정책을 찾을 수 없습니다. id=" + input.performanceId()));

        final LocalDateTime now = LocalDateTime.now(clock);
        final OrderAcceptanceStatus status = policy.acceptanceStatus(now);
        final BookingMode bookingMode = toBookingMode(policy, status, now);

        return new Output(
                input.performanceId(),
                status,
                bookingMode,
                policy.getOrderAcceptanceWindow().getOpensAt(),
                policy.getOrderAcceptanceWindow().getClosesAt(),
                policy.maxSeatCount(),
                policy.holdDuration().getSeconds()
        );
    }

    private BookingMode toBookingMode(
            final PerformanceSalesPolicy policy,
            final OrderAcceptanceStatus status,
            final LocalDateTime now
    ) {
        if (status != OrderAcceptanceStatus.OPEN) {
            return BookingMode.UNAVAILABLE;
        }
        return policy.isQueueRequired(now) ? BookingMode.QUEUE : BookingMode.DIRECT;
    }
}
