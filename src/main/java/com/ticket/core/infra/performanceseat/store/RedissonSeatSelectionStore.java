package com.ticket.core.infra.performanceseat.store;

import com.ticket.core.domain.performanceseat.store.SeatSelectionStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedissonSeatSelectionStore implements SeatSelectionStore {

    private static final String SELECT_IF_ABSENT_SCRIPT = """
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            redis.call('zremrangebyscore', KEYS[2], '-inf', nowMillis)
            local acquired = redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[1], 'NX')
            if not acquired then
                return 0
            end
            local expiresAt = nowMillis + ARGV[1]
            redis.call('zadd', KEYS[2], expiresAt, ARGV[3])
            local latest = redis.call('zrevrange', KEYS[2], 0, 0, 'WITHSCORES')
            redis.call('pexpireat', KEYS[2], latest[2])
            return 1
            """;
    private static final String RELEASE_IF_OWNED_SCRIPT = """
            if redis.call('get', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('del', KEYS[1])
            redis.call('zrem', KEYS[2], ARGV[2])
            return 1
            """;
    /**
     * 만료분은 score 범위 필터로 제외되므로 조회 경로에서 인덱스를 정리하지 않는다.
     * 정리는 selectIfAbsent, releaseIfOwned와 인덱스 키 자체의 TTL이 담당하고,
     * 멤버 수 상한은 회차 좌석 수다.
     */
    private static final String READ_ACTIVE_SEAT_IDS_SCRIPT = """
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            return redis.call('zrangebyscore', KEYS[1], '(' .. nowMillis, '+inf')
            """;

    private final RedissonClient redissonClient;

    @Override
    public boolean selectIfAbsent(final Long performanceId, final Long seatId, final String memberId, final Duration ttl) {
        final Long result = script().eval(
                RScript.Mode.READ_WRITE,
                SELECT_IF_ABSENT_SCRIPT,
                RScript.ReturnType.LONG,
                selectionKeys(performanceId, seatId),
                ttl.toMillis(),
                memberId,
                seatId.toString()
        );
        return result == 1L;
    }

    @Override
    public String getHolder(final Long performanceId, final Long seatId) {
        return bucket(performanceId, seatId).get();
    }

    @Override
    public boolean releaseIfOwned(final Long performanceId, final Long seatId, final String memberId) {
        final Long result = script().eval(
                RScript.Mode.READ_WRITE,
                RELEASE_IF_OWNED_SCRIPT,
                RScript.ReturnType.LONG,
                selectionKeys(performanceId, seatId),
                memberId,
                seatId.toString()
        );
        return result == 1L;
    }

    @Override
    public List<Long> releaseAllByMember(final Long performanceId, final String memberId) {
        final List<Long> deselectedSeatIds = new ArrayList<>();
        for (final Long seatId : getSelectingSeatIds(performanceId)) {
            if (releaseIfOwned(performanceId, seatId, memberId)) {
                deselectedSeatIds.add(seatId);
            }
        }
        return deselectedSeatIds;
    }

    @Override
    public Set<Long> getSelectingSeatIds(final Long performanceId) {
        final List<String> seatIds = script().eval(
                RScript.Mode.READ_ONLY,
                READ_ACTIVE_SEAT_IDS_SCRIPT,
                RScript.ReturnType.LIST,
                List.<Object>of(SeatSelectionRedisKey.selectSeatIndex(performanceId))
        );
        final Set<Long> result = HashSet.newHashSet(seatIds.size());
        seatIds.forEach(seatId -> result.add(Long.valueOf(seatId)));
        return result;
    }

    private RBucket<String> bucket(final Long performanceId, final Long seatId) {
        return redissonClient.getBucket(SeatSelectionRedisKey.select(performanceId, seatId), StringCodec.INSTANCE);
    }

    private RScript script() {
        return redissonClient.getScript(StringCodec.INSTANCE);
    }

    private List<Object> selectionKeys(final Long performanceId, final Long seatId) {
        return List.of(
                SeatSelectionRedisKey.select(performanceId, seatId),
                SeatSelectionRedisKey.selectSeatIndex(performanceId)
        );
    }
}
