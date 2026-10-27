package com.ticket.booking.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderSeat;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.testsupport.persistence.JpaSliceTestSupport;

@SuppressWarnings("NonAsciiCharacters")
class OrderRepositoryIntegrationTest extends JpaSliceTestSupport {
    private static final long MEMBER_ID = 100L;
    private static final long PERFORMANCE_ID = 200L;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        inTransaction(() -> entityManager
                .createQuery("select o from Order o", Order.class)
                .getResultList()
                .forEach(entityManager::remove));
    }

    /**
     * OrderSeat는 Order aggregate의 자식이라 자기 Repository 없이 root 저장에 cascade로 함께 실린다. 정렬은 {@code @OrderBy("id ASC")}가 옛
     * {@code findAllByOrderIdOrderByIdAsc}의 순서를 그대로 유지한다 — hold 생성 후처리가 이 순서의 seatId 목록을 쓴다.
     */
    @Test
    void 주문_좌석은_root_저장에_cascade되고_id_오름차순으로_복원된다() {
        Order order = order("with-seats", LocalDateTime.now().plusMinutes(5));
        order.addOrderSeat(501L, 42L, BigDecimal.valueOf(10_000), "R", "R석", "1F 가구역 A열 1번");
        order.addOrderSeat(502L, 43L, BigDecimal.valueOf(12_000), "S", "S석", "1F 가구역 A열 2번");

        inTransaction(() -> orderRepository.save(order));

        Order reloaded = inTransactionWithResult(() -> {
            Order found = orderRepository.findById(order.getId()).orElseThrow();
            found.getOrderSeats().size();
            return found;
        });
        assertThat(reloaded.getOrderSeats())
                .extracting(orderSeat -> orderSeat.getPerformanceSeatId())
                .containsExactly(501L, 502L);
        assertThat(reloaded.getOrderSeats())
                .allSatisfy(
                        orderSeat -> assertThat(orderSeat.getOrder().getId()).isEqualTo(order.getId()));
    }

    /**
     * 주문 상세 조회다. 좌석까지 {@code join fetch}로 한 번에 채워 오므로 트랜잭션 밖에서 좌석을 읽어도 lazy 초기화가 일어나지 않는다 — 여기서 좌석을 읽는 것 자체가 fetch
     * join이 실제로 걸렸다는 검증이다. 순서는 {@code @OrderBy("id ASC")}가 fetch join SQL에 그대로 붙어 보존된다.
     */
    @Test
    void 주문상세는_좌석까지_한번에_채워_조회한다() {
        Order order = order("detail", LocalDateTime.now().plusMinutes(5));
        order.addOrderSeat(501L, 42L, BigDecimal.valueOf(120_000), "R", "R석", "1F 가구역 A열 1번");
        order.addOrderSeat(502L, 43L, BigDecimal.valueOf(100_000), "S", "S석", "1F 가구역 A열 2번");
        inTransaction(() -> orderRepository.save(order));

        Order found = inTransactionWithResult(() -> orderRepository
                .findDetailByOrderKeyAndMemberId(order.getOrderKey(), MEMBER_ID)
                .orElseThrow());

        assertThat(found.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(found.getPerformanceId()).isEqualTo(PERFORMANCE_ID);
        assertThat(found.getShowTitleSnapshot()).isEqualTo("show-title");
        assertThat(found.getVenueNameSnapshot()).isEqualTo("venue-name");
        assertThat(found.getOrderSeats())
                .extracting(
                        OrderSeat::getPerformanceSeatId,
                        OrderSeat::getSeatId,
                        OrderSeat::getGradeCodeSnapshot,
                        OrderSeat::getSeatLabelSnapshot)
                .containsExactly(tuple(501L, 42L, "R", "1F 가구역 A열 1번"), tuple(502L, 43L, "S", "1F 가구역 A열 2번"));
        assertThat(found.getOrderSeats().getFirst().getUnitPrice()).isEqualByComparingTo("120000");
    }

    @Test
    void 다른_회원의_주문키로는_조회되지_않는다() {
        Order order = order("other-member", LocalDateTime.now().plusMinutes(5));
        order.addOrderSeat(501L, 42L, BigDecimal.valueOf(120_000), "R", "R석", "1F 가구역 A열 1번");
        inTransaction(() -> orderRepository.save(order));

        assertThat(orderRepository.findDetailByOrderKeyAndMemberId(order.getOrderKey(), MEMBER_ID + 1))
                .isEmpty();
        assertThat(orderRepository.findByOrderKeyAndMemberId(order.getOrderKey(), MEMBER_ID + 1))
                .isEmpty();
    }

    @Test
    void 주문키와_회원으로_주문상태를_조회한다() {
        Order order = order("status", LocalDateTime.now().plusMinutes(5));
        inTransaction(() -> orderRepository.save(order));

        Order found = inTransactionWithResult(() -> orderRepository
                .findByOrderKeyAndMemberId(order.getOrderKey(), MEMBER_ID)
                .orElseThrow());

        assertThat(found.getOrderKey()).isEqualTo(order.getOrderKey());
        assertThat(found.getStatus()).isEqualTo(OrderState.PENDING);
        assertThat(found.getExpiresAt()).isNotNull();
    }

    @Test
    void 만료_조회는_만료_시각이_딱_지금인_PENDING도_포함하고_id_순서로_하나만_잠근다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);
        Order future = order("future", now.plusSeconds(1), MEMBER_ID + 13);
        Order alreadyConfirmed = order("confirmed", now.minusMinutes(1));
        alreadyConfirmed.confirm(now.minusSeconds(1));
        Order boundary = order("boundary", now, MEMBER_ID + 12);
        Order past = order("past", now.minusSeconds(1), MEMBER_ID + 11);
        inTransaction(() -> saveAll(List.of(future, alreadyConfirmed, boundary, past)));

        String first = inTransactionWithResult(() -> orderRepository
                .findNextExpirableForUpdateSkipLocked(now, 0L)
                .orElseThrow()
                .getOrderKey());
        String afterBoundary = inTransactionWithResult(() -> orderRepository
                .findNextExpirableForUpdateSkipLocked(now, boundary.getId())
                .orElseThrow()
                .getOrderKey());

        assertThat(first).isEqualTo("order-boundary");
        assertThat(afterBoundary).isEqualTo("order-past");
        assertThat(inTransactionWithResult(
                        () -> orderRepository.findNextExpirableForUpdateSkipLocked(now, past.getId())))
                .isEmpty();
    }

    /** 다른 인스턴스가 잠근 주문은 기다리지 않고 건너뛴다. 그래야 모든 인스턴스가 함께 청소해도 서로 막지 않는다. */
    @Test
    void 만료_조회는_다른_트랜잭션이_잠근_주문을_기다리지_않고_건너뛴다() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);
        Order first = order("skip-first", now.minusMinutes(2), MEMBER_ID + 41);
        Order second = order("skip-second", now.minusMinutes(1), MEMBER_ID + 42);
        inTransaction(() -> saveAll(List.of(first, second)));
        ExecutorService executor = Executors.newFixedThreadPool(1);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<String> holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            String key = orderRepository
                    .findNextExpirableForUpdateSkipLocked(now, 0L)
                    .orElseThrow()
                    .getOrderKey();
            firstLocked.countDown();
            await(releaseFirst);
            return key;
        }));

        try {
            assertThat(firstLocked.await(3, TimeUnit.SECONDS)).isTrue();

            String skipped = inTransactionWithResult(() -> orderRepository
                    .findNextExpirableForUpdateSkipLocked(now, 0L)
                    .orElseThrow()
                    .getOrderKey());

            assertThat(skipped).isEqualTo("order-skip-second");
            releaseFirst.countDown();
            assertThat(holder.get(3, TimeUnit.SECONDS)).isEqualTo("order-skip-first");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 회원과_회차의_PENDING_주문을_잠가_조회한다() {
        Order pending = order("member-pending", LocalDateTime.now().minusMinutes(1));
        inTransaction(() -> orderRepository.save(pending));

        assertThat(inTransactionWithResult(() -> orderRepository
                        .findPendingByMemberIdAndPerformanceIdForUpdate(MEMBER_ID, PERFORMANCE_ID)
                        .map(Order::getOrderKey)))
                .contains("order-member-pending");
        assertThat(inTransactionWithResult(() ->
                        orderRepository.findPendingByMemberIdAndPerformanceIdForUpdate(MEMBER_ID + 1, PERFORMANCE_ID)))
                .isEmpty();
    }

    @Test
    void pending_order_existence_query_checks_member_and_status() {
        Order pending = order("pending-exists", LocalDateTime.now().plusMinutes(5));
        Order canceled = new Order(
                MEMBER_ID + 2,
                PERFORMANCE_ID,
                "order-canceled-exists",
                "hold-canceled-exists",
                LocalDateTime.now().plusMinutes(5),
                "show",
                LocalDateTime.now().plusDays(1),
                "venue");
        canceled.cancel(LocalDateTime.now());
        inTransaction(() -> saveAll(List.of(pending, canceled)));

        assertThat(orderRepository.existsPendingByMemberIdAndPerformanceId(MEMBER_ID, PERFORMANCE_ID))
                .isTrue();
        assertThat(orderRepository.existsPendingByMemberIdAndPerformanceId(MEMBER_ID + 1, PERFORMANCE_ID))
                .isFalse();
        assertThat(orderRepository.existsPendingByMemberIdAndPerformanceId(MEMBER_ID + 2, PERFORMANCE_ID))
                .isFalse();
    }

    @Test
    void pessimistic_write_lock_blocks_a_second_transaction_for_the_same_order() throws Exception {
        Order saved = inTransactionWithResult(
                () -> orderRepository.save(order("lock", LocalDateTime.now().plusMinutes(5))));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);

        Future<Boolean> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            boolean found = orderRepository
                    .findByOrderKeyAndMemberIdForUpdate(saved.getOrderKey(), MEMBER_ID)
                    .isPresent();
            firstLockAcquired.countDown();
            await(releaseFirstLock);
            return found;
        }));

        try {
            assertThat(firstLockAcquired.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> {
                secondTransactionStarted.countDown();
                return new TransactionTemplate(transactionManager)
                        .execute(status -> orderRepository
                                .findByOrderKeyAndMemberIdForUpdate(saved.getOrderKey(), MEMBER_ID)
                                .isPresent());
            });

            assertThat(secondTransactionStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            releaseFirstLock.countDown();

            assertThat(first.get(3, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private Order order(final String suffix, final LocalDateTime expiresAt) {
        return order(suffix, expiresAt, MEMBER_ID);
    }

    /** 회원·회차당 PENDING 주문은 하나다(uk_orders_pending_member_performance). PENDING을 여럿 만드는 테스트는 회원을 다르게 준다. */
    private Order order(final String suffix, final LocalDateTime expiresAt, final long memberId) {
        return new Order(
                memberId,
                PERFORMANCE_ID,
                "order-" + suffix,
                "hold-" + suffix,
                expiresAt,
                "show-title",
                expiresAt.plusDays(1),
                "venue-name");
    }

    private void saveAll(final List<Order> orders) {
        orders.forEach(orderRepository::save);
    }

    private void inTransaction(final Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private <T> T inTransactionWithResult(final java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while waiting for the lock test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the lock test latch", e);
        }
    }
}
