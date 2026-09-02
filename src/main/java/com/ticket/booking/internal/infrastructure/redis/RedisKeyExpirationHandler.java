package com.ticket.booking.internal.infrastructure.redis;

public interface RedisKeyExpirationHandler {

    boolean supports(String expiredKey);

    void handle(String expiredKey);
}
