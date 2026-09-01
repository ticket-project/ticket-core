package com.ticket.core.infra.redis;

public interface RedisKeyExpirationHandler {

    boolean supports(String expiredKey);

    void handle(String expiredKey);
}
