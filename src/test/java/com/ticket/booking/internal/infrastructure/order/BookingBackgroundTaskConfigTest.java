package com.ticket.booking.internal.infrastructure.order;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class BookingBackgroundTaskConfigTest {

    @Test
    void executor_has_fixed_workers_a_bounded_queue_and_rejects_overflow() {
        final ThreadPoolTaskExecutor executor =
                new BookingBackgroundTaskConfig().bookingBackgroundTaskExecutor();
        executor.afterPropertiesSet();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("booking-background-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(256);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            executor.shutdown();
        }
    }
}
