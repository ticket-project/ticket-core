package com.ticket.booking.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 좌석 상태 relay 구독. 받은 순서대로 보내려고 전달은 단일 스레드다. */
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

    /**
     * 구독 연결을 쥐는 스레드. 전달 executor와 따로 둔다 — 컨테이너는 구독 executor가 없으면 전달 executor를 같이 쓰는데, Redisson 구독은 끝날 때까지 스레드 하나를 쥐어 단일
     * 스레드 전달 executor에 메시지를 넘길 스레드가 남지 않는다.
     */
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
            final RedisConnectionFactory redisConnectionFactory, final SeatStatusRelay seatStatusRelay) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // @Configuration proxy가 같은 싱글턴 빈을 돌려준다.
        container.setTaskExecutor(seatStatusRelayExecutor());
        container.setSubscriptionExecutor(seatStatusRelaySubscriptionExecutor());
        container.addMessageListener(seatStatusRelay, new ChannelTopic(SeatStatusRelay.CHANNEL));
        return container;
    }
}
