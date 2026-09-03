package com.ticket.catalog.internal.application.publicapi;

import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.catalog.internal.application.performance.query.BookingSeatPriceReadRepository;
import com.ticket.catalog.internal.domain.performance.policy.BookingPolicyValidator;
import com.ticket.catalog.internal.domain.performance.query.PerformanceBookingPolicySnapshot;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceRepository;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@link BookingPolicyLookup}의 catalog 소유 구현이다. 회차 예매 정책과 요청된 좌석의 등급 가격을
 * 한 번에 조회해 booking에게 scalar snapshot만 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class BookingPolicyLookupService implements BookingPolicyLookup {

    private final PerformanceRepository performanceRepository;
    private final BookingSeatPriceReadRepository bookingSeatPriceReadRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public BookingPolicySnapshot getBookingPolicy(final long performanceId, final List<Long> seatIds) {
        final PerformanceBookingPolicySnapshot policy = performanceRepository.findBookingPolicyById(performanceId)
                .orElseThrow(() -> new NotFoundException(
                        "공연을 찾을 수 없습니다. id=" + performanceId));

        final LocalDateTime now = LocalDateTime.now(clock);
        final boolean bookingOpen = isBookingOpen(policy, now);
        final boolean queueRequired = BookingPolicyValidator.requiresQueue(policy, now);
        final Map<Long, BigDecimal> seatPrices =
                bookingSeatPriceReadRepository.findSeatPrices(performanceId, seatIds);

        return new BookingPolicySnapshot(
                performanceId,
                policy.showId(),
                bookingOpen,
                policy.orderOpenTime(),
                policy.orderCloseTime(),
                policy.maxCanHoldCount(),
                policy.holdTime(),
                policy.queueMode() == null ? null : policy.queueMode().name(),
                policy.queueLevel() == null ? null : policy.queueLevel().name(),
                policy.preopenQueueStartAt(),
                queueRequired,
                seatPrices
        );
    }

    private boolean isBookingOpen(final PerformanceBookingPolicySnapshot policy, final LocalDateTime now) {
        if (policy.orderOpenTime() == null || now.isBefore(policy.orderOpenTime())) {
            return false;
        }
        return policy.orderCloseTime() != null && !now.isAfter(policy.orderCloseTime());
    }
}
