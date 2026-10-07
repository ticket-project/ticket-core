package com.ticket.booking.redis;

public interface RedisKeyExpirationHandler {
    /** 자기 형식의 만료 키면 처리하고 true, 아니면 아무것도 하지 않고 false를 돌려준다. */
    boolean handle(String expiredKey);
}
