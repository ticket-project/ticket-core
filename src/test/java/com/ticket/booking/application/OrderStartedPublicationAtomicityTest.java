package com.ticket.booking.application;

import com.ticket.TicketApplication;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldAllocation;
import com.ticket.booking.domain.PendingOrderCreationResult;
import com.ticket.booking.domain.OrderState;
import com.ticket.booking.domain.OrderRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 8 Step 2: booking DB 트랜잭션과 {@code OrderStarted} event publication이 원자적으로
 * 함께 저장되거나 함께 사라지는지 확인한다.
 *
 * <p>Modulith의 JPA event publication registry는 {@code ApplicationEventPublisher.publishEvent}를
 * 호출한 트랜잭션의 {@code EntityManager}를 그대로 타므로, 커밋되면 event_publication row가 남고
 * 롤백되면 함께 사라진다. 이 계약은 실제 event 저장 방식(entity scan, serialization)에 의존하므로
 * 전체 애플리케이션 컨텍스트({@link TicketApplication})로 확인한다 — 좁은 슬라이스 컨텍스트는
 * Modulith event entity를 entity-scan하지 않아 이 계약을 재현하지 못한다.
 */
@SuppressWarnings({"NonAsciiCharacters", "resource"})
@SpringBootTest(
        classes = TicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:order-started-publication-test;MODE=Oracle;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "app.seed.enabled=false",
                "app.seed.load-test-fixture.enabled=false",
                "JWT_SECRET=0123456789abcdef0123456789abcdef",
                "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=1800",
                "JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=1209600",
                "GOOGLE_CLIENT_ID=order-started-publication-test",
                "GOOGLE_CLIENT_SECRET=order-started-publication-test",
                "KAKAO_CLIENT_ID=order-started-publication-test",
                "KAKAO_CLIENT_SECRET=order-started-publication-test",
                "KAKAO_ADMIN_KEY=order-started-publication-test",
                "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
                "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
        }
)
class OrderStartedPublicationAtomicityTest {

    private static final long MEMBER_ID = 900L;
    private static final long PERFORMANCE_ID = 901L;
    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);
    private static final int REDIS_PORT = 6379;

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private CreatePendingOrderTransactionService createPendingOrderTransactionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 성공하면_주문과_OrderStarted_publication이_함께_저장된다() {
        final String holdKey = "atomicity-success-" + System.nanoTime();
        final HoldAllocation allocation = allocationWithPersistedSeat(holdKey);

        final PendingOrderCreationResult result = createPendingOrderTransactionService.create(
                MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, allocation, saleSnapshotFor(allocation));

        assertThat(orderRepository.findById(result.order().getId())).isPresent();
        // listener가 비동기로 매우 빨리 완료돼 event_publication -> event_publication_archive로
        // 옮겨갈 수 있으므로, 두 테이블 합산이 안정적으로 1이 될 때까지 짧게 기다린다.
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> countPublicationsFor(holdKey) == 1L);
    }

    @Test
    void 롤백되면_주문과_publication이_모두_없다() {
        final String holdKey = "atomicity-rollback-" + System.nanoTime();
        final HoldAllocation allocation = allocationWithPersistedSeat(holdKey);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createPendingOrderTransactionService.create(
                    MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, allocation, saleSnapshotFor(allocation));
            throw new IllegalStateException("의도적인 롤백");
        })).isInstanceOf(IllegalStateException.class);

        final Boolean orderExists = new TransactionTemplate(transactionManager).execute(status ->
                orderRepository.findByHoldKeyAndStatusForUpdate(holdKey, OrderState.PENDING).isPresent());
        assertThat(orderExists).isFalse();
        assertThat(countPublicationsFor(holdKey)).isEqualTo(0L);
    }

    @Test
    void PendingOrderCreationResult는_order_하나만_담고_outbox_id를_담지_않는다() {
        final RecordComponent[] components = PendingOrderCreationResult.class.getRecordComponents();

        assertThat(components)
                .extracting(RecordComponent::getName)
                .containsExactly("order");
    }

    /**
     * 이 holdKey의 {@code OrderStarted} publication이 (완료 전이든, {@code completion-mode: archive}로
     * 이미 archive로 옮겨졌든) 몇 건 존재하는지 센다.
     *
     * <p>listener가 매우 빨리(비동기로) 완료되면 이 테스트가 확인하기 전에 이미 {@code event_publication}
     * 에서 {@code event_publication_archive}로 옮겨질 수 있어 두 테이블을 함께 본다. 이 테스트가
     * 확인하려는 것은 완료 여부가 아니라 "트랜잭션과 함께 publication이 남았는가"이다.
     */
    long countPublicationsFor(final String holdKey) {
        final Number count = new TransactionTemplate(transactionManager).execute(status ->
                (Number) entityManager.createNativeQuery(
                                "select "
                                        + "(select count(*) from event_publication where serialized_event like ?1) + "
                                        + "(select count(*) from event_publication_archive where serialized_event like ?1)")
                        .setParameter(1, "%" + holdKey + "%")
                        .getSingleResult());
        return count.longValue();
    }

    private HoldAllocation allocationWithPersistedSeat(final String holdKey) {
        final PerformanceSeat seat = persistSeat();
        final LocalDateTime expiresAt = LocalDateTime.now().plus(HOLD_DURATION);
        final Hold hold = new Hold(holdKey, MEMBER_ID, PERFORMANCE_ID, List.of(seat.getSeatId()), expiresAt);
        return new HoldAllocation(hold, List.of(seat));
    }

    private com.ticket.show.PerformanceSaleSnapshot saleSnapshotFor(final HoldAllocation allocation) {
        final java.util.Map<Long, com.ticket.show.PerformanceSaleSnapshot.SeatInfo> seatInfoBySeatId =
                new java.util.HashMap<>();
        for (final PerformanceSeat seat : allocation.performanceSeats()) {
            seatInfoBySeatId.put(seat.getSeatId(),
                    new com.ticket.show.PerformanceSaleSnapshot.SeatInfo(seat.getSeatId(), 1, "가", "A", "1"));
        }
        return new com.ticket.show.PerformanceSaleSnapshot(
                PERFORMANCE_ID, 1L, "show-title", 1L, "venue-name", LocalDateTime.now().plusDays(1),
                seatInfoBySeatId,
                java.util.Map.of(1L, new com.ticket.show.PerformanceSaleSnapshot.GradeInfo(
                        1L, "R", "R석", 1, BigDecimal.valueOf(10_000)))
        );
    }

    private PerformanceSeat persistSeat() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            final PerformanceSeat seat = new PerformanceSeat(
                    PERFORMANCE_ID,
                    (long) (Math.random() * 1_000_000_000L),
                    1L,
                    PerformanceSeatState.AVAILABLE,
                    BigDecimal.valueOf(10_000)
            );
            entityManager.persist(seat);
            entityManager.flush();
            return seat;
        });
    }
}
