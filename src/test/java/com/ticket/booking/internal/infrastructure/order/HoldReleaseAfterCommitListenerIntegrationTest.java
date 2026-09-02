package com.ticket.booking.internal.infrastructure.order;

import com.ticket.booking.internal.infrastructure.order.outbox.release.HoldReleaseOutboxExecutor;
import com.ticket.booking.internal.application.event.HoldReleaseRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(HoldReleaseAfterCommitListenerIntegrationTest.TestConfig.class)
class HoldReleaseAfterCommitListenerIntegrationTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private HoldReleaseOutboxExecutor outboxExecutor;

    @BeforeEach
    void resetMock() {
        reset(outboxExecutor);
    }

    @Test
    void committed_transaction_processes_the_outbox_after_commit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new HoldReleaseRequestedEvent(99L))
        );

        verify(outboxExecutor).process(99L, FIXED_NOW);
    }

    @Test
    void rolled_back_transaction_does_not_process_the_outbox() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new HoldReleaseRequestedEvent(99L));
            status.setRollbackOnly();
        });

        verifyNoInteractions(outboxExecutor);
    }

    @Test
    void event_without_a_transaction_is_not_processed() {
        eventPublisher.publishEvent(new HoldReleaseRequestedEvent(99L));

        verifyNoInteractions(outboxExecutor);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(final DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-28T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        }

        @Bean
        HoldReleaseOutboxExecutor outboxExecutor() {
            return mock(HoldReleaseOutboxExecutor.class);
        }

        @Bean
        HoldReleaseAfterCommitListener listener(
                final HoldReleaseOutboxExecutor outboxExecutor,
                final Clock clock,
                final TaskExecutor taskExecutor
        ) {
            return new HoldReleaseAfterCommitListener(outboxExecutor, clock, taskExecutor);
        }

        @Bean
        TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }
}
