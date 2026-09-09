package com.ticket.booking.infrastructure;

public interface RedisKeyExpirationHandler {

    boolean supports(String expiredKey);

    void handle(String expiredKey);
}
