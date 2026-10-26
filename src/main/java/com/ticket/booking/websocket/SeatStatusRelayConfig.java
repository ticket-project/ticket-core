package com.ticket.booking.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 좌석 상태 relay 구독. 만료 리스너와 다른 컨테이너를 쓴다 — 컨테이너 하나는 dispatch executor 하나를 쓰는데, 만료는 여러 스레드로 처리하고 relay는 순서를 지키려고 단일 스레드로
 * 처리해야 한다.
 */
@Configuration
public class SeatStatusRelayConfig {
    @Bean
    public ThreadPoolTaskExecutor seatStatusRelayExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 순서를 지키려고 스레드는 하나다. 큐는 상한 없이 둔다 — 밀리면 메모리에 쌓이므로 executor_queued_tasks로 본다(D2-1).
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("seat-status-relay-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor seatStatusRelaySubscriptionExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("seat-status-relay-subscription-");
        return executor;
    }

    @Bean
    public RedisMessageListenerContainer seatStatusRelayListenerContainer(
            final RedisConnectionFactory redisConnectionFactory, final SeatStatusRelaySubscriber subscriber) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // @Configuration proxy가 같은 싱글턴 빈을 돌려준다.
        container.setTaskExecutor(seatStatusRelayExecutor());
        container.setSubscriptionExecutor(seatStatusRelaySubscriptionExecutor());
        container.addMessageListener(subscriber, new ChannelTopic(RedisSeatStatusEventPublisher.CHANNEL));
        return container;
    }
}
