package com.ticket.booking.hold.persistence;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ticket.booking.order.usecase.ExpireOrderUseCase;
import com.ticket.booking.redis.RedisKeyExpirationHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldKeyExpirationHandler implements RedisKeyExpirationHandler {
    private final ExpireOrderUseCase expireOrderUseCase;
    private final Clock clock;

    @Override
    public boolean handle(final String expiredKey) {
        final Optional<String> holdKey = HoldRedisKey.tryParseHoldMetaKey(expiredKey);
        if (holdKey.isEmpty()) {
            return false;
        }
        expireOrderUseCase.expireByHoldKey(holdKey.get(), LocalDateTime.now(clock));
        log.info("홀드 만료 이벤트 처리: holdKey={}", holdKey.get());
        return true;
    }
}
