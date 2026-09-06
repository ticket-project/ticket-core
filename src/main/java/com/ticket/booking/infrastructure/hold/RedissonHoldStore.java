package com.ticket.booking.infrastructure.hold;

import com.ticket.booking.domain.hold.model.Hold;
import com.ticket.booking.domain.hold.store.HoldStore;
import com.ticket.booking.infrastructure.performanceseat.store.SeatSelectionRedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonHoldStore implements HoldStore {

    private final RedissonClient redissonClient;
    private final HoldMetaCodec holdMetaCodec;

    @Override
    public void save(final Hold hold, final Duration ttl) {
        final List<String> createdKeys = new ArrayList<>();
        try {
            final RSetCache<Long> holdSeatIndex = holdSeatIndex(hold.performanceId());
            for (final Long seatId : hold.seatIds()) {
                final String key = SeatSelectionRedisKey.hold(hold.performanceId(), seatId);
                redissonClient.getBucket(key, StringCodec.INSTANCE).set(hold.holdKey(), ttl);
                holdSeatIndex.add(seatId, ttl.toMillis(), TimeUnit.MILLISECONDS);
                createdKeys.add(key);
            }
            final String json = holdMetaCodec.encode(hold);
            redissonClient.getBucket(SeatSelectionRedisKey.holdMeta(hold.holdKey()), StringCodec.INSTANCE).set(json, ttl);
        } catch (final Exception e) {
            rollback(createdKeys, hold.holdKey());
            throw new IllegalStateException("hold Redis 저장에 실패했습니다.", e);
        }
    }

    @Override
    public List<Long> release(final Long performanceId, final String holdKey, final List<Long> seatIds) {
        final Hold storedHold = readHold(holdKey);
        final List<Long> normalizedSeatIds = seatIds.stream().distinct().sorted().toList();
        final RSetCache<Long> holdSeatIndex = holdSeatIndex(performanceId);
        final List<Long> releasedSeatIds = new ArrayList<>();
        boolean fullyReleased = storedHold == null
                || new HashSet<>(normalizedSeatIds).containsAll(storedHold.seatIds());
        for (final Long seatId : normalizedSeatIds) {
            final RBucket<String> bucket = redissonClient.getBucket(SeatSelectionRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
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
            redissonClient.getBucket(SeatSelectionRedisKey.holdMeta(holdKey), StringCodec.INSTANCE).delete();
        }
        return releasedSeatIds;
    }

    @Override
    public Set<Long> getHoldingSeatIds(final Long performanceId) {
        return new HashSet<>(holdSeatIndex(performanceId).readAll());
    }

    @Override
    public boolean isHeld(final Long performanceId, final Long seatId) {
        final RBucket<String> bucket = redissonClient.getBucket(SeatSelectionRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
        return bucket.get() != null;
    }

    @Override
    public boolean isHeldBy(final Long performanceId, final Long seatId, final String holdKey) {
        final RBucket<String> bucket = redissonClient.getBucket(SeatSelectionRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
        return Objects.equals(bucket.get(), holdKey);
    }

    private void rollback(final List<String> createdKeys, final String holdKey) {
        for (final String key : createdKeys) {
            try {
                redissonClient.getBucket(key, StringCodec.INSTANCE).delete();
            } catch (final Exception rollbackException) {
                log.warn("홀드 롤백에 실패했습니다. key={}", key, rollbackException);
            }
        }
        try {
            redissonClient.getBucket(SeatSelectionRedisKey.holdMeta(holdKey), StringCodec.INSTANCE).delete();
        } catch (final Exception rollbackException) {
            log.warn("홀드 메타 롤백에 실패했습니다. holdKey={}", holdKey, rollbackException);
        }
    }

    private RSetCache<Long> holdSeatIndex(final Long performanceId) {
        return redissonClient.getSetCache(SeatSelectionRedisKey.holdSeatIndex(performanceId), LongCodec.INSTANCE);
    }

    private Hold readHold(final String holdKey) {
        final RBucket<String> metaBucket = redissonClient.getBucket(SeatSelectionRedisKey.holdMeta(holdKey), StringCodec.INSTANCE);
        final String payload = metaBucket.get();
        if (payload == null) {
            return null;
        }
        return holdMetaCodec.decode(payload);
    }
}
