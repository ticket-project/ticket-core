package com.ticket.booking.selection.persistence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import com.ticket.booking.selection.domain.SeatSelectionStore;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedissonSeatSelectionStore implements SeatSelectionStore {
    /** 회원별 인덱스는 만료된 선택을 이 시간만큼 더 남긴다. 주문이 "선택 시간이 지났다"와 "선택한 적 없다"를 구분하는 근거다. 이보다 오래 지난 만료는 선택한 적 없는 것으로 본다. */
    private static final Duration EXPIRED_SELECTION_RETENTION = Duration.ofMinutes(10);

    /**
     * 좌석 키와 두 인덱스(KEYS[2] 회차 전체, KEYS[3] 회원별)를 함께 기록한다. 인덱스 score는 만료 시각이라, 좌석 키가 TTL로 사라지면 활성 조회에서도 score 필터로 같은 시각에
     * 빠진다. 회원별 인덱스만 만료분을 ARGV[5]ms 더 남기므로 한도는 zcard가 아니라 활성 구간 zcount로 센다. ARGV[4]가 빈 문자열이면 회원 한도가 없다. 반환값은 1 선택, 0 이미
     * 선택된 좌석, 2 회원 한도 초과다.
     */
    private static final String SELECT_IF_ABSENT_SCRIPT = """
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            redis.call('zremrangebyscore', KEYS[2], '-inf', nowMillis)
            redis.call('zremrangebyscore', KEYS[3], '-inf', nowMillis - ARGV[5])
            if redis.call('exists', KEYS[1]) == 1 then
                return 0
            end
            if ARGV[4] ~= '' and redis.call('zcount', KEYS[3], '(' .. nowMillis, '+inf') >= tonumber(ARGV[4]) then
                return 2
            end
            redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[1])
            local expiresAt = nowMillis + ARGV[1]
            for i = 2, 3 do
                redis.call('zadd', KEYS[i], expiresAt, ARGV[3])
                local latest = redis.call('zrevrange', KEYS[i], 0, 0, 'WITHSCORES')
                local retention = 0
                if i == 3 then
                    retention = ARGV[5]
                end
                redis.call('pexpireat', KEYS[i], latest[2] + retention)
            end
            return 1
            """;

    private static final String RELEASE_IF_OWNED_SCRIPT = """
            if redis.call('get', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('del', KEYS[1])
            redis.call('zrem', KEYS[2], ARGV[2])
            redis.call('zrem', KEYS[3], ARGV[2])
            return 1
            """;

    /**
     * 만료분은 score 범위 필터로 제외되므로 조회 경로에서 인덱스를 정리하지 않는다. 정리는 selectIfAbsent, releaseIfOwned와 인덱스 키 자체의 TTL이 담당하고, 멤버 수 상한은
     * 회차 인덱스가 회차 좌석 수, 회원별 인덱스가 회원 한도다.
     */
    private static final String READ_ACTIVE_SEAT_IDS_SCRIPT = """
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            return redis.call('zrangebyscore', KEYS[1], '(' .. nowMillis, '+inf')
            """;

    /** 만료된 지 ARGV[1]ms 이내인 선택. 정리 시점에 따라 더 오래된 만료가 남아 있을 수 있어 범위로 자른다. */
    private static final String READ_RECENTLY_EXPIRED_SEAT_IDS_SCRIPT = """
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            return redis.call('zrangebyscore', KEYS[1], nowMillis - ARGV[1], nowMillis)
            """;

    private final RedissonClient redissonClient;

    @Override
    public SelectResult selectIfAbsent(
            final Long performanceId,
            final Long seatId,
            final String memberId,
            final Duration ttl,
            final @Nullable Integer maxSeatCount) {
        final Long result = script().eval(
                        RScript.Mode.READ_WRITE,
                        SELECT_IF_ABSENT_SCRIPT,
                        RScript.ReturnType.LONG,
                        selectionKeys(performanceId, seatId, memberId),
                        ttl.toMillis(),
                        memberId,
                        seatId.toString(),
                        maxSeatCount == null ? "" : maxSeatCount.toString(),
                        EXPIRED_SELECTION_RETENTION.toMillis());
        if (result == 1L) {
            return SelectResult.SELECTED;
        }
        return result == 2L ? SelectResult.LIMIT_EXCEEDED : SelectResult.ALREADY_SELECTED;
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
                        selectionKeys(performanceId, seatId, memberId),
                        memberId,
                        seatId.toString());
        return result == 1L;
    }

    @Override
    public List<Long> releaseAllByMember(final Long performanceId, final String memberId) {
        final List<Long> deselectedSeatIds = new ArrayList<>();
        for (final Long seatId : getSelectedSeatIdsByMember(performanceId, memberId)) {
            if (releaseIfOwned(performanceId, seatId, memberId)) {
                deselectedSeatIds.add(seatId);
            }
        }
        return deselectedSeatIds;
    }

    @Override
    public Set<Long> getSelectingSeatIds(final Long performanceId) {
        return readActiveSeatIds(SeatSelectionRedisKey.selectSeatIndex(performanceId));
    }

    @Override
    public Set<Long> getSelectedSeatIdsByMember(final Long performanceId, final String memberId) {
        return readActiveSeatIds(SeatSelectionRedisKey.selectMemberIndex(performanceId, memberId));
    }

    @Override
    public Set<Long> getRecentlyExpiredSeatIdsByMember(final Long performanceId, final String memberId) {
        final List<String> seatIds = script().eval(
                        RScript.Mode.READ_ONLY,
                        READ_RECENTLY_EXPIRED_SEAT_IDS_SCRIPT,
                        RScript.ReturnType.LIST,
                        List.<Object>of(SeatSelectionRedisKey.selectMemberIndex(performanceId, memberId)),
                        EXPIRED_SELECTION_RETENTION.toMillis());
        return toSeatIds(seatIds);
    }

    private Set<Long> readActiveSeatIds(final String indexKey) {
        final List<String> seatIds = script().eval(
                        RScript.Mode.READ_ONLY,
                        READ_ACTIVE_SEAT_IDS_SCRIPT,
                        RScript.ReturnType.LIST,
                        List.<Object>of(indexKey));
        return toSeatIds(seatIds);
    }

    private Set<Long> toSeatIds(final List<String> seatIds) {
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

    private List<Object> selectionKeys(final Long performanceId, final Long seatId, final String memberId) {
        return List.of(
                SeatSelectionRedisKey.select(performanceId, seatId),
                SeatSelectionRedisKey.selectSeatIndex(performanceId),
                SeatSelectionRedisKey.selectMemberIndex(performanceId, memberId));
    }
}
