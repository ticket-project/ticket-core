package com.ticket.booking.infrastructure.redis;

public interface RedisKeyExpirationHandler {

    boolean supports(String expiredKey);

    void handle(String expiredKey);
}
