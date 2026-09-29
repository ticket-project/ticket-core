package com.ticket.booking.redis;

public interface RedisKeyExpirationHandler {
    boolean canHandle(String expiredKey);

    void handle(String expiredKey);
}
