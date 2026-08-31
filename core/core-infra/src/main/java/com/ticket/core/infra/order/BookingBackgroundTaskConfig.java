package com.ticket.core.app.order.command;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class BookingBackgroundTaskConfig {

    public static final String BOOKING_BACKGROUND_TASK_EXECUTOR = "bookingBackgroundTaskExecutor";

    private static final int WORKER_COUNT = 2;
    private static final int QUEUE_CAPACITY = 256;

    @Bean(name = BOOKING_BACKGROUND_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor bookingBackgroundTaskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(WORKER_COUNT);
        executor.setMaxPoolSize(WORKER_COUNT);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("booking-background-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
