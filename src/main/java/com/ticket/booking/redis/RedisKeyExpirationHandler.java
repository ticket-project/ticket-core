package com.ticket.booking.redis;

public interface RedisKeyExpirationHandler {
    boolean supports(String expiredKey);

    void handle(String expiredKey);
}
