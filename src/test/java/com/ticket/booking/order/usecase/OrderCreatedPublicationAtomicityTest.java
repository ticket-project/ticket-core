package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticket.TicketApplication;
import com.ticket.booking.OrderCreated;
import com.ticket.booking.OrderTerminated;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.bootstrap.support.BookingE2ETestSupport;

import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 생성 트랜잭션이 {@code OrderCreated} publication 없이 주문만 남기는지, 이미 저장된 publication JSON은 계속 읽히는지 확인한다. 주문 생성은 2026-11-06부터
 * publication을 만들지 않는다. 옛 publication은 운영 DB에 남아 있을 수 있어 listener와 역직렬화는 유지한다.
 *
 * <p>아래 원래 설명은 publication이 남는지 볼 때도 같은 이유로 전체 컨텍스트가 필요하다는 뜻이다.
 *
 * <p>Modulith의 JPA event publication registry는 {@code ApplicationEventPublisher.publishEvent}를 호출한 트랜잭션의
 * {@code EntityManager}를 그대로 타므로, 커밋되면 event_publication row가 남고 롤백되면 함께 사라진다. 이 계약은 실제 event 저장 방식(entity scan,
 * serialization)에 의존하므로 전체 애플리케이션 컨텍스트({@link TicketApplication})로 확인한다 — 좁은 슬라이스 컨텍스트는 Modulith event entity를
 * entity-scan하지 않아 이 계약을 재현하지 못한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class OrderCreatedPublicationAtomicityTest extends BookingE2ETestSupport {
    private static final long MEMBER_ID = 900L;
    private static final long PERFORMANCE_ID = 901L;
    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    @Autowired
    private PendingOrderCreator pendingOrderCreator;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    /**
     * Modulith는 이 JsonMapper로 publication을 직렬화한다. 이미 저장된 publication JSON을 eventId·schemaVersion까지 그대로 읽어 재처리할 수 있어야
     * 한다.
     */
    @Test
    void 저장된_publication_JSON을_읽는다() {
        final String created =
                "{\"eventId\":\"0b0e7c1c-9a55-4b5e-9a49-6d1f0c3c2a11\",\"schemaVersion\":1,\"orderId\":1,"
                        + "\"memberId\":2,\"holdKey\":\"hold\",\"performanceSeatIds\":[3],\"occurredAt\":\"2026-10-01T00:00:00Z\"}";
        final String terminated =
                "{\"eventId\":\"0b0e7c1c-9a55-4b5e-9a49-6d1f0c3c2a11\",\"schemaVersion\":1,\"orderId\":1,"
                        + "\"memberId\":2,\"holdKey\":\"hold\",\"performanceSeatIds\":[3],\"reason\":\"EXPIRED\","
                        + "\"occurredAt\":\"2026-10-01T00:00:00Z\"}";

        final OrderCreated readCreated = jsonMapper.readValue(created, OrderCreated.class);
        assertThat(readCreated.orderId()).isEqualTo(1L);
        assertThat(readCreated.eventId()).hasToString("0b0e7c1c-9a55-4b5e-9a49-6d1f0c3c2a11");
        // schema 1에는 회차·좌석 값이 없다. listener는 performanceId가 null이면 주문을 다시 읽는다.
        assertThat(readCreated.schemaVersion()).isEqualTo(1);
        assertThat(readCreated.performanceId()).isNull();
        assertThat(readCreated.seatIds()).isEmpty();
        assertThat(readCreated.performanceSeatIdBySeatId()).isEmpty();
        final OrderCreated current = new OrderCreated(
                readCreated.eventId(),
                OrderCreated.SCHEMA_VERSION,
                1L,
                2L,
                "hold",
                Set.of(3L),
                readCreated.occurredAt(),
                4L,
                List.of(5L),
                Map.of(5L, 3L));
        assertThat(jsonMapper.readValue(jsonMapper.writeValueAsString(current), OrderCreated.class))
                .isEqualTo(current);
        final OrderTerminated readTerminated = jsonMapper.readValue(terminated, OrderTerminated.class);
        assertThat(readTerminated.reason()).isEqualTo("EXPIRED");
        assertThat(readTerminated.schemaVersion()).isEqualTo(OrderTerminated.SCHEMA_VERSION);
    }

    /** 주문 생성 후처리는 커밋 뒤 바로 한다(2026-11-06). 주문은 저장되고 {@code OrderCreated} publication은 남지 않는다 — 보관함 문장 다섯 개를 쓰지 않는다. */
    @Test
    void 성공하면_주문만_저장되고_OrderCreated_publication은_남지_않는다() {
        final String holdKey = "atomicity-success-" + System.nanoTime();
        final Hold hold = holdWithPersistedSeat(holdKey);
        final List<PerformanceSeat> performanceSeats = persistedSeats(holdKey);

        final String orderKey = pendingOrderCreator.create(
                MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, hold, performanceSeats, saleSnapshotFor(performanceSeats));

        assertThat(orderKey).isNotBlank();
        final Boolean savedOrderExists = new TransactionTemplate(transactionManager)
                .execute(status ->
                        orderRepository.findPendingByHoldKeyForUpdate(holdKey).isPresent());
        assertThat(savedOrderExists).isTrue();
        assertThat(countPublicationsFor(holdKey)).isEqualTo(0L);
    }

    @Test
    void 롤백되면_주문과_publication이_모두_없다() {
        final String holdKey = "atomicity-rollback-" + System.nanoTime();
        final Hold hold = holdWithPersistedSeat(holdKey);
        final List<PerformanceSeat> performanceSeats = persistedSeats(holdKey);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    pendingOrderCreator.create(
                            MEMBER_ID,
                            PERFORMANCE_ID,
                            HOLD_DURATION,
                            hold,
                            performanceSeats,
                            saleSnapshotFor(performanceSeats));
                    throw new IllegalStateException("의도적인 롤백");
                }))
                .isInstanceOf(IllegalStateException.class);

        final Boolean orderExists = new TransactionTemplate(transactionManager)
                .execute(status ->
                        orderRepository.findPendingByHoldKeyForUpdate(holdKey).isPresent());
        assertThat(orderExists).isFalse();
        assertThat(countPublicationsFor(holdKey)).isEqualTo(0L);
    }

    /**
     * 주문 생성 트랜잭션은 entity가 아니라 orderKey만 돌려준다. 트랜잭션 밖에서 lazy 연관을 다시 읽는 경로를 만들지 않기 위해서다 — 옛
     * {@code PendingOrderCreationResult}는 Order 하나만 감싸는 래퍼라 없앴다.
     */
    @Test
    void 주문_생성_트랜잭션은_orderKey만_반환한다() throws NoSuchMethodException {
        assertThat(PendingOrderCreator.class
                        .getDeclaredMethod(
                                "create",
                                Long.class,
                                Long.class,
                                Duration.class,
                                Hold.class,
                                List.class,
                                com.ticket.show.api.PerformanceSaleSnapshot.class)
                        .getReturnType())
                .isEqualTo(String.class);
    }

    /**
     * 이 holdKey의 {@code OrderCreated} publication이 (완료 전이든, {@code completion-mode: archive}로 이미 archive로 옮겨졌든) 몇 건
     * 존재하는지 센다.
     *
     * <p>listener가 매우 빨리(비동기로) 완료되면 이 테스트가 확인하기 전에 이미 {@code event_publication} 에서 {@code event_publication_archive}로
     * 옮겨질 수 있어 두 테이블을 함께 본다. 이 테스트가 확인하려는 것은 완료 여부가 아니라 "트랜잭션과 함께 publication이 남았는가"이다.
     */
    long countPublicationsFor(final String holdKey) {
        final Number count = new TransactionTemplate(transactionManager).execute(status -> (Number) entityManager
                .createNativeQuery("select "
                        + "(select count(*) from event_publication where serialized_event like ?1) + "
                        + "(select count(*) from event_publication_archive where serialized_event like ?1)")
                .setParameter(1, "%" + holdKey + "%")
                .getSingleResult());
        return count.longValue();
    }

    private final java.util.Map<String, List<PerformanceSeat>> seatsByHoldKey = new java.util.HashMap<>();

    private Hold holdWithPersistedSeat(final String holdKey) {
        final PerformanceSeat seat = persistSeat();
        seatsByHoldKey.put(holdKey, List.of(seat));
        return new Hold(
                holdKey,
                MEMBER_ID,
                PERFORMANCE_ID,
                List.of(seat.getSeatId()),
                LocalDateTime.now().plus(HOLD_DURATION));
    }

    private List<PerformanceSeat> persistedSeats(final String holdKey) {
        return seatsByHoldKey.get(holdKey);
    }

    private com.ticket.show.api.PerformanceSaleSnapshot saleSnapshotFor(final List<PerformanceSeat> performanceSeats) {
        final java.util.Map<Long, com.ticket.show.api.PerformanceSaleSnapshot.SeatInfo> seatInfoBySeatId =
                new java.util.HashMap<>();
        for (final PerformanceSeat seat : performanceSeats) {
            seatInfoBySeatId.put(
                    seat.getSeatId(),
                    new com.ticket.show.api.PerformanceSaleSnapshot.SeatInfo(seat.getSeatId(), 1, "가", "A", "1"));
        }
        return new com.ticket.show.api.PerformanceSaleSnapshot(
                PERFORMANCE_ID,
                1L,
                "show-title",
                1L,
                "venue-name",
                LocalDateTime.now().plusDays(1),
                seatInfoBySeatId,
                java.util.Map.of(
                        1L,
                        new com.ticket.show.api.PerformanceSaleSnapshot.GradeInfo(
                                1L, "R", "R석", 1, BigDecimal.valueOf(10_000))));
    }

    private PerformanceSeat persistSeat() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            final PerformanceSeat seat = new PerformanceSeat(
                    PERFORMANCE_ID,
                    (long) (Math.random() * 1_000_000_000L),
                    1L,
                    PerformanceSeatState.AVAILABLE,
                    BigDecimal.valueOf(10_000));
            entityManager.persist(seat);
            entityManager.flush();
            return seat;
        });
    }
}
