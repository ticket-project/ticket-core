package com.ticket.booking.salespolicy.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.salespolicy.domain.BookingWindowStatus;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;

import lombok.RequiredArgsConstructor;

/**
 * 인증 없이 회차의 진입 방식을 조회하는 booking 소유 use case다. 안내용 조회이므로 실제 좌석 선택·상태·주문 API는 이 결과와 무관하게 실행 시점에 정책을 다시 검사한다. FE 라우트나
 * ticket-queue HTTP 경로는 담지 않는다 — {@code bookingMode}는 업무 의미만 전달한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceEntryModeUseCase {
    private final PerformanceSaleFinder performanceSaleFinder;
    private final Clock clock;

    public record Input(Long performanceId) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
        }
    }

    public enum EntryMode {
        DIRECT,
        QUEUE,
        UNAVAILABLE
    }

    public record Output(
            Long performanceId,
            BookingWindowStatus acceptanceStatus,
            EntryMode bookingMode,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            Integer maxSeatCount,
            long holdDurationSeconds) {}

    public Output execute(final Input input) {
        final PerformanceSalesPolicy policy = performanceSaleFinder.requirePolicy(input.performanceId());

        final LocalDateTime now = LocalDateTime.now(clock);
        final BookingWindowStatus status = policy.bookingWindowStatus(now);
        final EntryMode bookingMode = toEntryMode(policy, status, now);

        return new Output(
                input.performanceId(),
                status,
                bookingMode,
                policy.getBookingWindow().getOpensAt(),
                policy.getBookingWindow().getClosesAt(),
                policy.maxSeatCount(),
                policy.holdDuration().getSeconds());
    }

    private EntryMode toEntryMode(
            final PerformanceSalesPolicy policy, final BookingWindowStatus status, final LocalDateTime now) {
        if (status != BookingWindowStatus.OPEN) {
            return EntryMode.UNAVAILABLE;
        }
        return policy.isQueueRequired(now) ? EntryMode.QUEUE : EntryMode.DIRECT;
    }
}
