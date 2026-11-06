package com.ticket.booking.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticket.booking.OrderTerminated;
import com.ticket.bootstrap.support.BookingE2ETestSupport;

/**
 * Modulith listener id가 package 이동 뒤에도 옛 값으로 유지되는지 고정한다.
 *
 * <p>{@code BookingEventListeners}는 {@code com.ticket.booking.application}에서 지금의 {@code com.ticket.booking.event}로
 * 옮겨졌다. Spring의 기본 listener id는 선언 클래스의 FQCN을 포함하므로(Spring Framework
 * {@code ApplicationListenerMethodAdapter#getDefaultListenerId}), 아무것도 하지 않으면 id가 바뀐다. 그러면 이동 전에 저장돼 아직 완료되지 않은
 * {@code EVENT_PUBLICATION} row는 어떤 listener의 것인지 매칭되지 않아 영원히 재처리되지 않는다.
 *
 * <p>그래서 두 가지를 함께 고정한다.
 *
 * <ol>
 *   <li>새로 저장되는 publication의 {@code listener_id}가 여전히 옛 package 문자열이다.
 *   <li>그 옛 id로 저장된 미완료 publication을 재처리하면 실제로 listener가 돌아 완료된다 — 이동 전 남아 있던 publication의 회귀 사례다.
 * </ol>
 */
@SuppressWarnings("NonAsciiCharacters")
class BookingEventListenerIdContractTest extends BookingE2ETestSupport {
    private static final long MEMBER_ID = 940L;
    /** 없는 주문이다. listener는 주문을 찾지 못하면 후처리 없이 완료한다 — 여기서는 id 매칭과 완료만 본다. */
    private static final long MISSING_ORDER_ID = 9_400_000_000L;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private IncompleteEventPublications incompleteEventPublications;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void OrderTerminated_publication은_옛_package_경로의_listener_id로_저장된다() {
        final String holdKey = "listener-id-" + System.nanoTime();

        publishOrderTerminated(holdKey);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> !listenerIdsFor(holdKey).isEmpty());

        assertThat(listenerIdsFor(holdKey))
                .as("이동 전에 저장된 publication과 매칭되려면 옛 package 문자열이어야 한다")
                .containsExactly(BookingEventListeners.ORDER_TERMINATED_LISTENER_ID);
    }

    @Test
    void 옛_listener_id로_저장된_미완료_publication은_이동_후에도_재처리된다() {
        final String holdKey = "listener-id-resubmit-" + System.nanoTime();
        publishOrderTerminated(holdKey);
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> !listenerIdsFor(holdKey).isEmpty());
        // 이동 전 시점에 저장돼 아직 완료되지 않은 publication을 그대로 재현한다.
        final UUID staleId = UUID.randomUUID();
        insertIncompletePublication(staleId, holdKey);

        incompleteEventPublications.resubmitIncompletePublications(
                publication -> staleId.equals(publication.getIdentifier()));

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> isCompleted(staleId));
    }

    private void insertIncompletePublication(final UUID id, final String holdKey) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            final Object[] source = (Object[]) entityManager
                    .createNativeQuery("select serialized_event, event_type from event_publication "
                            + "where serialized_event like ?1 "
                            + "union all "
                            + "select serialized_event, event_type from event_publication_archive "
                            + "where serialized_event like ?1")
                    .setParameter(1, "%" + holdKey + "%")
                    .setMaxResults(1)
                    .getSingleResult();

            entityManager
                    .createNativeQuery("insert into event_publication "
                            + "(id, publication_date, listener_id, serialized_event, event_type, "
                            + " completion_date, last_resubmission_date, completion_attempts, status) "
                            + "values (?1, ?2, ?3, ?4, ?5, null, null, 0, 'PUBLISHED')")
                    .setParameter(1, id)
                    .setParameter(2, java.time.OffsetDateTime.now())
                    .setParameter(3, BookingEventListeners.ORDER_TERMINATED_LISTENER_ID)
                    .setParameter(4, source[0])
                    .setParameter(5, source[1])
                    .executeUpdate();
        });
    }

    private boolean isCompleted(final UUID id) {
        final Number count = new TransactionTemplate(transactionManager).execute(status -> (Number) entityManager
                .createNativeQuery("select "
                        + "(select count(*) from event_publication "
                        + " where id = ?1 and completion_date is not null) + "
                        + "(select count(*) from event_publication_archive where id = ?1)")
                .setParameter(1, id)
                .getSingleResult());
        return count.longValue() > 0L;
    }

    @SuppressWarnings("unchecked")
    private List<String> listenerIdsFor(final String holdKey) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> entityManager
                        .createNativeQuery("select listener_id from event_publication where serialized_event like ?1 "
                                + "union all "
                                + "select listener_id from event_publication_archive "
                                + "where serialized_event like ?1")
                        .setParameter(1, "%" + holdKey + "%")
                        .getResultList());
    }

    /** 주문 종료 이벤트를 트랜잭션 안에서 발행한다. listener가 있으면 Modulith가 publication을 저장한다. */
    private void publishOrderTerminated(final String holdKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(new OrderTerminated(
                        UUID.randomUUID(),
                        OrderTerminated.SCHEMA_VERSION,
                        MISSING_ORDER_ID,
                        MEMBER_ID,
                        holdKey,
                        Set.of(11L),
                        "CANCELED",
                        Instant.now())));
    }
}
