package com.ticket.booking.selection.persistence;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import com.ticket.booking.redis.RedisKeyExpirationHandler;
import com.ticket.booking.selection.usecase.SeatSelectionWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 좌석 선택 TTL 만료 키를 해석해 application에 넘긴다. <b>Redis key 해석, 처리권 확보, 호출만 한다</b> — 대상 좌석 확인, 현재 상태 확인, 알림 필요 여부 판단은
 * {@link SeatSelectionWriter}가 좌석 락 안에서 수행한다.
 *
 * <p><b>인스턴스가 여러 대면 한 대만 처리한다.</b> keyspace 만료 알림은 모든 인스턴스가 받는다. 키를 해석한 뒤 {@code SET expired-claim:<만료된 키> <인스턴스 id> NX
 * PX 60000}에 성공한 인스턴스만 처리하고, {@code DESELECTED}는 relay로 모든 인스턴스에 간다(스케일 아웃 결정 목록 D2-2).
 *
 * <ul>
 *   <li>해석에 성공한 키만 claim한다. 모든 키를 claim하면 표시 키 자신의 만료가 다시 claim되는 고리가 생긴다.
 *   <li>claim은 작업 큐에서 꺼낸 뒤에 한다. 한 인스턴스의 큐가 claim TTL보다 오래 밀리면 같은 만료를 한 번 더 처리할 수 있고(화면은 같다), 4분 넘게 밀리면 같은 좌석의 다음 만료 알림이
 *       빠질 수 있다. 화면 알림이라 다음 조회에서 맞춰진다.
 *   <li>claim TTL은 같은 좌석의 선택 키가 다시 만료되는 최소 간격({@code SeatSelectionService.SELECT_TTL})보다 짧아야 한다. 그렇지 않으면 이전 claim이 다음
 *       만료를 막는다.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionExpirationHandler implements RedisKeyExpirationHandler {
    static final Duration CLAIM_TTL = Duration.ofSeconds(60);
    private static final String CLAIM_KEY_PREFIX = "expired-claim:";
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final SeatSelectionWriter seatSelectionWriter;
    private final RedissonClient redissonClient;

    @Override
    public boolean handle(final String expiredKey) {
        final Optional<SeatSelectionRedisKey.SelectKey> parsed = SeatSelectionRedisKey.tryParseSelectKey(expiredKey);
        if (parsed.isEmpty()) {
            return false;
        }
        if (!claim(expiredKey)) {
            log.debug("다른 인스턴스가 처리하는 좌석 선택 만료입니다. expiredKey={}", expiredKey);
            return true;
        }
        final SeatSelectionRedisKey.SelectKey selectKey = parsed.get();
        seatSelectionWriter.notifyReleasedIfFree(selectKey.performanceId(), selectKey.seatId());
        log.info("좌석 선택 만료 이벤트 처리: performanceId={}, seatId={}", selectKey.performanceId(), selectKey.seatId());
        return true;
    }

    private boolean claim(final String expiredKey) {
        return redissonClient
                .getBucket(CLAIM_KEY_PREFIX + expiredKey, StringCodec.INSTANCE)
                .setIfAbsent(INSTANCE_ID, CLAIM_TTL);
    }
}
