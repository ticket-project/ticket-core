package com.ticket.core.infra.hold;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.hold.store.HoldSnapshotCodec;
import com.ticket.core.domain.hold.store.HoldStore;
import com.ticket.core.domain.performanceseat.support.SeatRedisKey;
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
    private final HoldSnapshotCodec holdSnapshotCodec;

    @Override
    public void save(final HoldSnapshot snapshot, final Duration ttl) {
        final List<String> createdKeys = new ArrayList<>();
        try {
            final RSetCache<Long> holdSeatIndex = holdSeatIndex(snapshot.performanceId());
            for (final Long seatId : snapshot.seatIds()) {
                final String key = SeatRedisKey.hold(snapshot.performanceId(), seatId);
                redissonClient.getBucket(key, StringCodec.INSTANCE).set(snapshot.holdKey(), ttl);
                holdSeatIndex.add(seatId, ttl.toMillis(), TimeUnit.MILLISECONDS);
                createdKeys.add(key);
            }
            final String json = holdSnapshotCodec.encode(snapshot);
            redissonClient.getBucket(SeatRedisKey.holdMeta(snapshot.holdKey()), StringCodec.INSTANCE).set(json, ttl);
        } catch (final Exception e) {
            rollback(createdKeys, snapshot.holdKey());
            throw new IllegalStateException("hold Redis 저장에 실패했습니다.", e);
        }
    }

    @Override
    public List<Long> release(final Long performanceId, final String holdKey, final List<Long> seatIds) {
        final HoldSnapshot snapshot = readSnapshot(holdKey);
        final List<Long> normalizedSeatIds = seatIds.stream().distinct().sorted().toList();
        final RSetCache<Long> holdSeatIndex = holdSeatIndex(performanceId);
        final List<Long> releasedSeatIds = new ArrayList<>();
        boolean fullyReleased = snapshot == null
                || new HashSet<>(normalizedSeatIds).containsAll(snapshot.seatIds());
        for (final Long seatId : normalizedSeatIds) {
            final RBucket<String> bucket = redissonClient.getBucket(SeatRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
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
            redissonClient.getBucket(SeatRedisKey.holdMeta(holdKey), StringCodec.INSTANCE).delete();
        }
        return releasedSeatIds;
    }

    @Override
    public Set<Long> getHoldingSeatIds(final Long performanceId) {
        return new HashSet<>(holdSeatIndex(performanceId).readAll());
    }

    @Override
    public boolean isHeld(final Long performanceId, final Long seatId) {
        final RBucket<String> bucket = redissonClient.getBucket(SeatRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
        return bucket.get() != null;
    }

    @Override
    public boolean isHeldBy(final Long performanceId, final Long seatId, final String holdKey) {
        final RBucket<String> bucket = redissonClient.getBucket(SeatRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
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
            redissonClient.getBucket(SeatRedisKey.holdMeta(holdKey), StringCodec.INSTANCE).delete();
        } catch (final Exception rollbackException) {
            log.warn("홀드 메타 롤백에 실패했습니다. holdKey={}", holdKey, rollbackException);
        }
    }

    private RSetCache<Long> holdSeatIndex(final Long performanceId) {
        return redissonClient.getSetCache(SeatRedisKey.holdSeatIndex(performanceId), LongCodec.INSTANCE);
    }

    private HoldSnapshot readSnapshot(final String holdKey) {
        final RBucket<String> metaBucket = redissonClient.getBucket(SeatRedisKey.holdMeta(holdKey), StringCodec.INSTANCE);
        final String payload = metaBucket.get();
        if (payload == null) {
            return null;
        }
        return holdSnapshotCodec.decode(payload);
    }
}
