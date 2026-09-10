package com.ticket.booking.order.application;

import com.ticket.TicketApplication;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldAllocation;
import com.ticket.booking.order.domain.PendingOrderCreationResult;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import jakarta.persistence.EntityManager;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modulith listener id가 package 이동 뒤에도 옛 값으로 유지되는지 고정한다.
 *
 * <p>{@code BookingEventListeners}는 capability 이전으로 {@code com.ticket.booking.application}
 * 에서 {@code com.ticket.booking.order.application}으로 옮겨졌다. Spring의 기본 listener id는
 * 선언 클래스의 FQCN을 포함하므로(Spring Framework
 * {@code ApplicationListenerMethodAdapter#getDefaultListenerId}), 아무것도 하지 않으면 id가
 * 바뀐다. 그러면 이동 전에 저장돼 아직 완료되지 않은 {@code EVENT_PUBLICATION} row는 어떤
 * listener의 것인지 매칭되지 않아 영원히 재처리되지 않는다.
 *
 * <p>그래서 두 가지를 함께 고정한다.
 *
 * <ol>
 *   <li>새로 저장되는 publication의 {@code listener_id}가 여전히 옛 package 문자열이다.</li>
 *   <li>그 옛 id로 저장된 미완료 publication을 재제출하면 실제로 listener가 돌아 완료된다 —
 *       이동 전 남아 있던 publication의 회귀 사례다.</li>
 * </ol>
 */
@SuppressWarnings({"NonAsciiCharacters", "resource"})
@SpringBootTest(
        classes = TicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:listener-id-contract-test;MODE=Oracle;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "JWT_SECRET=0123456789abcdef0123456789abcdef",
                "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=1800",
                "JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=1209600",
                "GOOGLE_CLIENT_ID=listener-id-contract-test",
                "GOOGLE_CLIENT_SECRET=listener-id-contract-test",
                "KAKAO_CLIENT_ID=listener-id-contract-test",
                "KAKAO_CLIENT_SECRET=listener-id-contract-test",
                "KAKAO_ADMIN_KEY=listener-id-contract-test",
                "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
                "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
        }
)
class BookingEventListenerIdContractTest {

    private static final long MEMBER_ID = 940L;
    private static final long PERFORMANCE_ID = 941L;
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
    private IncompleteEventPublications incompleteEventPublications;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void OrderStarted_publication은_옛_package_경로의_listener_id로_저장된다() {
        final String holdKey = "listener-id-" + System.nanoTime();

        createPendingOrder(holdKey);

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> !listenerIdsFor(holdKey).isEmpty());

        assertThat(listenerIdsFor(holdKey))
                .as("이동 전에 저장된 publication과 매칭되려면 옛 package 문자열이어야 한다")
                .containsExactly(BookingEventListeners.ORDER_STARTED_LISTENER_ID);
    }

    @Test
    void 옛_listener_id로_저장된_미완료_publication은_이동_후에도_재처리된다() {
        final String holdKey = "listener-id-resubmit-" + System.nanoTime();
        createPendingOrder(holdKey);
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> !listenerIdsFor(holdKey).isEmpty());

        // 이동 전 시점에 저장돼 아직 완료되지 않은 publication을 그대로 재현한다.
        final UUID staleId = UUID.randomUUID();
        insertIncompletePublication(staleId, holdKey);

        incompleteEventPublications.resubmitIncompletePublications(
                publication -> staleId.equals(publication.getIdentifier()));

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> isCompleted(staleId));
    }

    private void insertIncompletePublication(final UUID id, final String holdKey) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            final Object[] source = (Object[]) entityManager.createNativeQuery(
                            "select serialized_event, event_type from event_publication "
                                    + "where serialized_event like ?1 "
                                    + "union all "
                                    + "select serialized_event, event_type from event_publication_archive "
                                    + "where serialized_event like ?1")
                    .setParameter(1, "%" + holdKey + "%")
                    .setMaxResults(1)
                    .getSingleResult();

            entityManager.createNativeQuery(
                            "insert into event_publication "
                                    + "(id, publication_date, listener_id, serialized_event, event_type, "
                                    + " completion_date, last_resubmission_date, completion_attempts, status) "
                                    + "values (?1, ?2, ?3, ?4, ?5, null, null, 0, 'PUBLISHED')")
                    .setParameter(1, id)
                    .setParameter(2, java.time.OffsetDateTime.now())
                    .setParameter(3, BookingEventListeners.ORDER_STARTED_LISTENER_ID)
                    .setParameter(4, source[0])
                    .setParameter(5, source[1])
                    .executeUpdate();
        });
    }

    private boolean isCompleted(final UUID id) {
        final Number count = new TransactionTemplate(transactionManager).execute(status ->
                (Number) entityManager.createNativeQuery(
                                "select "
                                        + "(select count(*) from event_publication "
                                        + " where id = ?1 and completion_date is not null) + "
                                        + "(select count(*) from event_publication_archive where id = ?1)")
                        .setParameter(1, id)
                        .getSingleResult());
        return count.longValue() > 0L;
    }

    @SuppressWarnings("unchecked")
    private List<String> listenerIdsFor(final String holdKey) {
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createNativeQuery(
                                "select listener_id from event_publication where serialized_event like ?1 "
                                        + "union all "
                                        + "select listener_id from event_publication_archive "
                                        + "where serialized_event like ?1")
                        .setParameter(1, "%" + holdKey + "%")
                        .getResultList());
    }

    private void createPendingOrder(final String holdKey) {
        final PerformanceSeat seat = persistSeat();
        final Hold hold = new Hold(holdKey, MEMBER_ID, PERFORMANCE_ID, List.of(seat.getSeatId()),
                LocalDateTime.now().plus(HOLD_DURATION));
        final HoldAllocation allocation = new HoldAllocation(hold, List.of(seat));

        final PendingOrderCreationResult result = createPendingOrderTransactionService.create(
                MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, allocation, saleSnapshotFor(allocation));

        assertThat(result.order().getId()).isNotNull();
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
                Map.of(1L, new com.ticket.show.PerformanceSaleSnapshot.GradeInfo(
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
