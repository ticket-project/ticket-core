package com.ticket.booking.order.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ticket.booking.domain.hold.HoldHistory;
import com.ticket.booking.domain.hold.HoldHistoryRepository;
import com.ticket.booking.domain.hold.HoldReleaseReason;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderSeat;

import lombok.RequiredArgsConstructor;

/**
 * 주문 모델({@link Order}/{@link OrderSeat})과 공연 좌석 모델({@link PerformanceSeat})을 선점 이력으로 변환해 기록한다.
 *
 * <p>이 조립은 hold의 규칙이 아니라 "주문이 선점 이력을 어떻게 남기는가"라 {@code order.application}이 소유한다. {@link
 * HoldHistory}와 {@link HoldHistoryRepository}는 hold aggregate의 것이라 {@code hold.domain}에 그대로 둔다.
 *
 * <p>호출자는 모두 주문 상태를 바꾸는 트랜잭션 안에 있다({@link PendingOrderCreator}, {@link OrderTerminationService}를
 * 부르는 취소·만료 트랜잭션). 이력 기록과 주문 상태 변경은 그 트랜잭션에서 함께 커밋되거나 함께 사라진다 — 여기에 별도 전파 설정을 두지 않는 것이 그 원자성을 지키는
 * 방법이다.
 */
@Component
@RequiredArgsConstructor
public class OrderHoldHistoryRecorder {
    private final HoldHistoryRepository holdHistoryRepository;

    public void recordCreated(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime occurredAt,
            final LocalDateTime expiresAt,
            final List<PerformanceSeat> performanceSeats) {
        holdHistoryRepository.saveAll(
                performanceSeats.stream()
                        .map(
                                seat ->
                                        HoldHistory.created(
                                                holdKey,
                                                memberId,
                                                performanceId,
                                                seat.getId(),
                                                seat.getSeatId(),
                                                occurredAt,
                                                expiresAt))
                        .toList());
    }

    public void recordCanceled(final Order order, final LocalDateTime occurredAt) {
        holdHistoryRepository.saveAll(
                order.getOrderSeats().stream()
                        .map(
                                seat ->
                                        HoldHistory.canceled(
                                                order.getHoldKey(),
                                                order.getMemberId(),
                                                order.getPerformanceId(),
                                                seat.getPerformanceSeatId(),
                                                seat.getSeatId(),
                                                occurredAt,
                                                HoldReleaseReason.USER_CANCELED))
                        .toList());
    }

    public void recordExpired(final Order order, final LocalDateTime occurredAt) {
        holdHistoryRepository.saveAll(
                order.getOrderSeats().stream()
                        .map(
                                seat ->
                                        HoldHistory.expired(
                                                order.getHoldKey(),
                                                order.getMemberId(),
                                                order.getPerformanceId(),
                                                seat.getPerformanceSeatId(),
                                                seat.getSeatId(),
                                                occurredAt,
                                                HoldReleaseReason.TTL_EXPIRED))
                        .toList());
    }
}
