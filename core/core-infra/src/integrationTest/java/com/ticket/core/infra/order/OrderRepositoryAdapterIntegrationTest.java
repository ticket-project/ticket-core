package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = OrderRepositoryAdapterIntegrationTest.TestApplication.class
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-repository-test;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV4"
})
@SuppressWarnings("NonAsciiCharacters")
class OrderRepositoryAdapterIntegrationTest {

    private static final long MEMBER_ID = 100L;
    private static final long PERFORMANCE_ID = 200L;
    private static final int BATCH_SIZE = 100;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SpringDataOrderJpaRepository jpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        inTransaction(() -> jpaRepository.deleteAll());
    }

    @Test
    void expiration_query_includes_orders_due_exactly_now() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);

        Order past = order("past", now.minusSeconds(1));
        Order boundary = order("boundary", now);
        Order future = order("future", now.plusSeconds(1));
        Order alreadyConfirmed = order("confirmed", now.minusMinutes(1));
        alreadyConfirmed.confirm(now.minusSeconds(1));

        inTransaction(() -> jpaRepository.saveAll(List.of(past, boundary, future, alreadyConfirmed)));

        List<Order> result = orderRepository.findExpirable(OrderState.PENDING, now, BATCH_SIZE);

        assertThat(result)
                .extracting(Order::getOrderKey)
                .containsExactly("order-past", "order-boundary");
    }

    @Test
    void expiration_query_respects_the_requested_limit() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 12, 0);
        inTransaction(() -> jpaRepository.saveAll(List.of(
                order("first", now.minusMinutes(3)),
                order("second", now.minusMinutes(2)),
                order("third", now.minusMinutes(1))
        )));

        List<Order> result = orderRepository.findExpirable(OrderState.PENDING, now, 2);

        assertThat(result)
                .extracting(Order::getOrderKey)
                .containsExactly("order-first", "order-second");
    }

    @Test
    void pending_order_existence_query_checks_member_performance_and_status() {
        Order pending = order("pending-exists", LocalDateTime.now().plusMinutes(5));
        inTransaction(() -> jpaRepository.save(pending));

        assertThat(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(
                MEMBER_ID,
                PERFORMANCE_ID,
                OrderState.PENDING
        )).isTrue();
        assertThat(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(
                MEMBER_ID + 1,
                PERFORMANCE_ID,
                OrderState.PENDING
        )).isFalse();
        assertThat(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(
                MEMBER_ID,
                PERFORMANCE_ID,
                OrderState.CONFIRMED
        )).isFalse();
    }

    @Test
    void pessimistic_write_lock_blocks_a_second_transaction_for_the_same_order() throws Exception {
        Order saved = inTransactionWithResult(() -> orderRepository.save(order("lock", LocalDateTime.now().plusMinutes(5))));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);

        Future<Boolean> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            boolean found = orderRepository.findByOrderKeyAndMemberIdForUpdate(saved.getOrderKey(), MEMBER_ID).isPresent();
            firstLockAcquired.countDown();
            await(releaseFirstLock);
            return found;
        }));

        try {
            assertThat(firstLockAcquired.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> {
                secondTransactionStarted.countDown();
                return new TransactionTemplate(transactionManager).execute(status ->
                        orderRepository.findByOrderKeyAndMemberIdForUpdate(saved.getOrderKey(), MEMBER_ID).isPresent()
                );
            });

            assertThat(secondTransactionStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstLock.countDown();

            assertThat(first.get(3, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private Order order(final String suffix, final LocalDateTime expiresAt) {
        return new Order(
                MEMBER_ID,
                PERFORMANCE_ID,
                "order-" + suffix,
                "hold-" + suffix,
                BigDecimal.valueOf(10_000),
                expiresAt
        );
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = {"com.ticket.core.domain", "com.ticket.core.infra"})
    @EnableJpaRepositories(basePackageClasses = SpringDataOrderJpaRepository.class)
    @EnableJpaAuditing
    @Import(OrderRepositoryAdapter.class)
    static class TestApplication {

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("integration-test");
        }
    }
}
