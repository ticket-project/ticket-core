package com.ticket.booking.support.infrastructure;

public interface RedisKeyExpirationHandler {

    boolean supports(String expiredKey);

    void handle(String expiredKey);
}
