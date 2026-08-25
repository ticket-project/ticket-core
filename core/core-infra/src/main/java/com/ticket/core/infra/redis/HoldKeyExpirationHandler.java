package com.ticket.core.infra.redis;

import com.ticket.core.app.order.command.ExpireOrderUseCase;
import com.ticket.core.domain.performanceseat.support.SeatRedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldKeyExpirationHandler implements RedisKeyExpirationHandler {

    private final ExpireOrderUseCase expireOrderUseCase;
    private final Clock clock;

    @Override
    public boolean supports(final String expiredKey) {
        return SeatRedisKey.tryParseHoldMetaKey(expiredKey).isPresent();
    }

    @Override
    public void handle(final String expiredKey) {
        final SeatRedisKey.HoldMetaKey holdMetaKey = SeatRedisKey.tryParseHoldMetaKey(expiredKey)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 홀드 만료 키입니다: " + expiredKey));

        expireOrderUseCase.expireByHoldKey(holdMetaKey.holdKey(), LocalDateTime.now(clock));
        log.info("홀드 만료 이벤트 처리: holdKey={}", holdMetaKey.holdKey());
    }
}
