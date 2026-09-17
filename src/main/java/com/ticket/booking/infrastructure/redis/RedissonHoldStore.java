package com.ticket.booking.infrastructure.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.redisson.api.RBucket;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import com.ticket.booking.domain.hold.Hold;
import com.ticket.booking.domain.hold.HoldStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonHoldStore implements HoldStore {
    private final RedissonClient redissonClient;
    private final HoldMetaCodec holdMetaCodec;

    /**
     * 좌석 키, 회차별 점유 인덱스, 메타데이터를 순서대로 쓴다. 어느 단계에서 실패하든 이 hold가 남긴 흔적만 되돌린다.
     *
     * <p>되돌릴 대상은 "쓰기를 시도한 좌석"이다 — 좌석 키를 쓰기 <b>전에</b> 기록해 둔다. 옛 구현은 인덱스 등록까지 끝난 뒤에야 기록해서, 인덱스 등록이
     * 실패하면 방금 쓴 좌석 키가 정리 대상에서 빠졌다. 인덱스 자체도 되돌리지 않아 좌석 키 없는 유령 점유가 인덱스에 남았다.
     *
     * <p>보상은 반드시 <b>소유 키를 확인하고</b> 지운다. TTL 만료 뒤 다른 요청이 같은 좌석을 새로 확보했을 수 있으므로, 값이 이 hold의 key일 때만
     * 삭제한다.
     */
    @Override
    public void save(final Hold hold, final Duration ttl) {
        final List<Long> touchedSeatIds = new ArrayList<>();
        try {
            final RSetCache<Long> holdSeatIndex = holdSeatIndex(hold.performanceId());
            for (final Long seatId : hold.seatIds()) {
                touchedSeatIds.add(seatId);
                seatBucket(hold.performanceId(), seatId).set(hold.holdKey(), ttl);
                holdSeatIndex.add(seatId, ttl.toMillis(), TimeUnit.MILLISECONDS);
            }
            final String json = holdMetaCodec.encode(hold);
            redissonClient
                    .getBucket(HoldRedisKey.holdMeta(hold.holdKey()), StringCodec.INSTANCE)
                    .set(json, ttl);
        } catch (final Exception e) {
            rollback(hold, touchedSeatIds);
            throw new IllegalStateException("hold Redis 저장에 실패했습니다.", e);
        }
    }

    @Override
    public List<Long> release(
            final Long performanceId, final String holdKey, final List<Long> seatIds) {
        final Hold storedHold = readHold(holdKey);
        final List<Long> normalizedSeatIds = seatIds.stream().distinct().sorted().toList();
        final RSetCache<Long> holdSeatIndex = holdSeatIndex(performanceId);
        final List<Long> releasedSeatIds = new ArrayList<>();
        boolean fullyReleased =
                storedHold == null
                        || new HashSet<>(normalizedSeatIds).containsAll(storedHold.seatIds());
        for (final Long seatId : normalizedSeatIds) {
            final RBucket<String> bucket = seatBucket(performanceId, seatId);
            final String storedHoldKey = bucket.get();
            if (holdKey.equals(storedHoldKey)) {
                holdSeatIndex.remove(seatId);
                bucket.delete();
                releasedSeatIds.add(seatId);
            } else {
                fullyReleased = false;
            }
        }
        if (fullyReleased) {
            redissonClient.getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE).delete();
        }
        return releasedSeatIds;
    }

    @Override
    public Set<Long> getHoldingSeatIds(final Long performanceId) {
        return new HashSet<>(holdSeatIndex(performanceId).readAll());
    }

    @Override
    public boolean isHeld(final Long performanceId, final Long seatId) {
        return seatBucket(performanceId, seatId).get() != null;
    }

    @Override
    public boolean isHeldBy(final Long performanceId, final Long seatId, final String holdKey) {
        return Objects.equals(seatBucket(performanceId, seatId).get(), holdKey);
    }

    private void rollback(final Hold hold, final List<Long> touchedSeatIds) {
        final RSetCache<Long> holdSeatIndex = holdSeatIndex(hold.performanceId());
        for (final Long seatId : touchedSeatIds) {
            try {
                rollbackSeat(hold, seatId, holdSeatIndex);
            } catch (final Exception rollbackException) {
                log.warn(
                        "홀드 롤백에 실패했습니다. performanceId={}, seatId={}",
                        hold.performanceId(),
                        seatId,
                        rollbackException);
            }
        }
        try {
            // 메타 key는 holdKey를 그대로 담고 있어 이 hold 전용이다. 다른 요청의 것을 지울 수 없다.
            redissonClient
                    .getBucket(HoldRedisKey.holdMeta(hold.holdKey()), StringCodec.INSTANCE)
                    .delete();
        } catch (final Exception rollbackException) {
            log.warn("홀드 메타 롤백에 실패했습니다. holdKey={}", hold.holdKey(), rollbackException);
        }
    }

    private void rollbackSeat(
            final Hold hold, final Long seatId, final RSetCache<Long> holdSeatIndex) {
        final RBucket<String> bucket = seatBucket(hold.performanceId(), seatId);
        final String storedHoldKey = bucket.get();
        if (hold.holdKey().equals(storedHoldKey)) {
            bucket.delete();
            holdSeatIndex.remove(seatId);
            return;
        }
        if (storedHoldKey == null) {
            // 좌석 키는 없는데 인덱스에만 남은 유령 점유다. 이 hold가 만들었을 수 있으므로 함께 지운다.
            holdSeatIndex.remove(seatId);
            return;
        }
        // 이미 다른 요청이 확보한 좌석이다. 보상이 남의 선점을 지우면 안 된다.
        log.warn(
                "홀드 롤백 대상 좌석이 다른 선점에 속해 건너뜁니다. performanceId={}, seatId={}, holdKey={}",
                hold.performanceId(),
                seatId,
                hold.holdKey());
    }

    private RBucket<String> seatBucket(final Long performanceId, final Long seatId) {
        return redissonClient.getBucket(
                HoldRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
    }

    private RSetCache<Long> holdSeatIndex(final Long performanceId) {
        return redissonClient.getSetCache(
                HoldRedisKey.holdSeatIndex(performanceId), LongCodec.INSTANCE);
    }

    private @Nullable Hold readHold(final String holdKey) {
        final RBucket<String> metaBucket =
                redissonClient.getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE);
        final String payload = metaBucket.get();
        if (payload == null) {
            return null;
        }
        return holdMetaCodec.decode(payload);
    }
}
