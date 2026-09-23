package com.ticket.booking.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticket.member.api.MemberWithdrawn;

@SpringJUnitConfig(MemberWithdrawalWebSocketListenerTest.Config.class)
class MemberWithdrawalWebSocketListenerTest {
    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MemberWebSocketSessions sessions;

    @BeforeEach
    void resetSessions() {
        reset(sessions);
    }

    @Test
    void closes_connections_only_after_commit() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new MemberWithdrawn(7L));
            verifyNoInteractions(sessions);
        });

        verify(sessions).close(7L);
    }

    @Test
    void rollback_does_not_close_connections() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new MemberWithdrawn(7L));
            status.setRollbackOnly();
        });

        verifyNoInteractions(sessions);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean
        MemberWebSocketSessions sessions() {
            return mock(MemberWebSocketSessions.class);
        }

        @Bean
        MemberWithdrawalWebSocketListener listener(final MemberWebSocketSessions sessions) {
            return new MemberWithdrawalWebSocketListener(sessions);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(
                    new DriverManagerDataSource("jdbc:h2:mem:withdrawal-websocket", "sa", ""));
        }
    }
}
