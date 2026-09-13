package com.ticket.booking.common;

public interface RedisKeyExpirationHandler {
    boolean supports(String expiredKey);

    void handle(String expiredKey);
}
