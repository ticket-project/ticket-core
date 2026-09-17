package com.ticket.booking.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisKeyExpirationListener implements MessageListener {
    private final List<RedisKeyExpirationHandler> handlers;

    @Override
    public void onMessage(final Message message, final byte @Nullable [] pattern) {
        final String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);

        handlers.stream()
                .filter(handler -> handler.supports(expiredKey))
                .findFirst()
                .ifPresent(handler -> handler.handle(expiredKey));
    }
}
