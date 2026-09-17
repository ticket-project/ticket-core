package com.ticket.booking.order.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.OrderStarted;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderKeyGenerator;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.show.api.PerformanceSaleSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 예매 시작의 DB 구간이다. 주문·주문 좌석·선점 이력·{@code OrderStarted} publication이 한 트랜잭션에서 함께 커밋되거나 함께 사라진다.
 *
 * <p>Redis 선점은 이 트랜잭션 밖에서 이미 끝났다. 여기서 실패하면 호출자({@link
 * com.ticket.booking.order.usecase.StartBookingUseCase})가 그 선점을 보상 해제한다.
 *
 * <p>주문 금액은 show가 준 표시값이 아니라 오직 {@link PerformanceSeat#getUnitPrice()}로 계산한다(ADR 0005) — 클라이언트가 보낸
 * 가격도, show가 다시 계산한 가격도 받지 않는다. 총액은 따로 더하지 않고 {@code Order.addOrderSeat}가 좌석 단가를 누적한다. 좌석은 Order
 * aggregate의 자식이라 별도 Repository 없이 root를 저장할 때 함께 저장된다.
 */
@Service
@RequiredArgsConstructor
public class PendingOrderCreator {
    private final OrderRepository orderRepository;
    private final OrderKeyGenerator orderKeyGenerator;
    private final OrderHoldHistoryRecorder orderHoldHistoryRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @return 만들어진 주문의 orderKey. 트랜잭션 밖에서 entity를 다시 만지지 않도록 필요한 값만 돌려준다 — lazy 연관을 트랜잭션 밖에서 읽는 경로를
     *     애초에 만들지 않는다.
     */
    @Transactional
    public String create(
            final Long memberId,
            final Long performanceId,
            final Duration holdDuration,
            final Hold hold,
            final List<PerformanceSeat> performanceSeats,
            final PerformanceSaleSnapshot saleSnapshot) {
        final Order order =
                new Order(
                        memberId,
                        performanceId,
                        orderKeyGenerator.generate(),
                        hold.holdKey(),
                        hold.expiresAt(),
                        // ORDERS의 이 세 컬럼은 NOT NULL이다 -- 표시값이 없는 공연은 주문이 성립하지 않는다.
                        // 여기서 막지 않아도 insert에서 제약 위반으로 같은 500이 났다.
                        Objects.requireNonNull(saleSnapshot.showTitle(), "showTitle"),
                        Objects.requireNonNull(
                                saleSnapshot.performanceStartTime(), "performanceStartTime"),
                        Objects.requireNonNull(saleSnapshot.venueName(), "venueName"));

        addSeatsToOrder(order, performanceSeats, saleSnapshot);

        final Order savedOrder = orderRepository.save(order);

        final LocalDateTime startedAt = hold.startedAt(holdDuration);
        orderHoldHistoryRecorder.recordCreated(
                memberId,
                performanceId,
                hold.holdKey(),
                startedAt,
                hold.expiresAt(),
                performanceSeats);

        eventPublisher.publishEvent(
                new OrderStarted(
                        UUID.randomUUID(),
                        OrderStarted.SCHEMA_VERSION,
                        savedOrder.getId(),
                        memberId,
                        hold.holdKey(),
                        performanceSeatIds(performanceSeats),
                        startedAt.atZone(clock.getZone()).toInstant()));

        return savedOrder.getOrderKey();
    }

    /** 주문 좌석마다 좌석 표시값과 등급 표시값을 붙여 Order에 더한다. 총액은 {@code addOrderSeat}가 좌석 단가로 누적한다. */
    private void addSeatsToOrder(
            final Order order,
            final List<PerformanceSeat> performanceSeats,
            final PerformanceSaleSnapshot saleSnapshot) {
        for (final PerformanceSeat seat : performanceSeats) {
            final PerformanceSaleSnapshot.SeatInfo seatInfo =
                    saleSnapshot.seatInfoBySeatId().get(seat.getSeatId());
            if (seatInfo == null) {
                throw new IllegalStateException("좌석 표시값을 찾을 수 없습니다. seatId=" + seat.getSeatId());
            }
            final PerformanceSaleSnapshot.GradeInfo gradeInfo =
                    saleSnapshot.gradeInfoByPerformanceGradeId().get(seat.getPerformanceGradeId());
            if (gradeInfo == null) {
                throw new IllegalStateException(
                        "등급 표시값을 찾을 수 없습니다. performanceGradeId=" + seat.getPerformanceGradeId());
            }
            order.addOrderSeat(
                    seat.getId(),
                    seat.getSeatId(),
                    seat.getUnitPrice(),
                    gradeInfo.gradeCode(),
                    gradeInfo.gradeName(),
                    seatInfo.label());
        }
    }

    private Set<Long> performanceSeatIds(final List<PerformanceSeat> performanceSeats) {
        return performanceSeats.stream()
                .map(PerformanceSeat::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
