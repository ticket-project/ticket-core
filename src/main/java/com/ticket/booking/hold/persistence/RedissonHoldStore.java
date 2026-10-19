package com.ticket.booking.hold.persistence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonHoldStore implements HoldStore {
    /**
     * KEYS: 1 회차 점유 인덱스, 2 메타 키, 3.. 좌석 키. ARGV: 1 인덱스 만료 시각(ms), 2 TTL(ms), 3 hold key, 4 메타 키 접두사, 5 회원 ID, 6.. 좌석 키와
     * 같은 순서의 seatId. 모두 쓰면 1, 이미 잡힌 좌석이 같은 회원의 선점이면 2, 그 밖에 이미 잡힌 좌석이 있으면 0. 메타 키의 값은 선점한 회원 ID다(값을 읽는 곳은 이 스크립트뿐이다).
     * Redis는 스크립트 중간 오류에서 앞선 쓰기를 되돌리지 않으므로, 실패할 수 있는 확인(인덱스 자료형)은 쓰기 전에 끝낸다.
     */
    private static final String SAVE_IF_ABSENT_SCRIPT = """
            local indexType = redis.call('type', KEYS[1])['ok']
            if indexType ~= 'none' and indexType ~= 'zset' then
                return redis.error_reply('WRONGTYPE hold seat index is ' .. indexType)
            end
            local result = 1
            for i = 3, #KEYS do
                local holder = redis.call('get', KEYS[i])
                if holder then
                    if redis.call('get', ARGV[4] .. holder) == ARGV[5] then
                        return 2
                    end
                    result = 0
                end
            end
            if result == 0 then
                return 0
            end
            for i = 3, #KEYS do
                redis.call('set', KEYS[i], ARGV[3], 'PX', ARGV[2])
                redis.call('zadd', KEYS[1], ARGV[1], ARGV[i + 3])
            end
            redis.call('set', KEYS[2], ARGV[5], 'PX', ARGV[2])
            return 1
            """;

    private final RedissonClient redissonClient;

    /**
     * 좌석 키·회차별 점유 인덱스·메타 키를 Lua 스크립트 한 번으로 쓴다. 좌석 하나라도 이미 잡혀 있으면 아무것도 쓰지 않고, 그 선점이 같은 회원 것인지 함께 알려 준다.
     *
     * <p>예전에는 좌석마다 SET NX와 인덱스 추가를 따로 보내고 메타 키를 마지막에 썼다(좌석 하나에 Redis 왕복 3번). 중간에 충돌하면 이미 쓴 좌석을 소유 키를 확인하며 되돌렸다. 스크립트는
     * 확인과 쓰기를 한 번에 하므로 다른 요청이 보는 중간 상태도, 되돌릴 흔적도 없다(gatling-test capacity-log 2026-10-19).
     *
     * <p>좌석 키는 hold key 문자열과 TTL, 메타 키는 선점한 회원 ID와 TTL이다(예전에는 hold key였지만 아무도 읽지 않았다). 인덱스는 Redisson {@code RSetCache}가
     * 쓰는 모양(만료 시각을 점수로 한 ZSET, 값은 seatId 문자열)이라 {@link #getHoldingSeatIds}·{@link #release}·Redisson 만료 정리가 그대로 읽는다.
     * 인덱스 객체를 만드는 것도 그대로 둬 Redisson 만료 정리 예약이 이전과 같다.
     *
     * <p>ponytail: 메타 키({@code hold:key:...})에는 회차 hash tag가 없어 Redis Cluster에서는 좌석 키와 다른 slot이라 한 스크립트에 넣을 수 없다. 지금은
     * Redis 한 대다. Cluster로 가면 메타 키에 회차 hash tag를 붙이고 만료 처리기의 key 해석을 함께 바꾼다.
     */
    @Override
    public SaveResult saveIfAbsent(final Hold hold, final Duration ttl) {
        final RSetCache<Long> holdSeatIndex = holdSeatIndex(hold.performanceId());
        final List<Object> keys =
                new ArrayList<>(List.of(holdSeatIndex.getName(), HoldRedisKey.holdMeta(hold.holdKey())));
        final List<Object> args = new ArrayList<>(List.of(
                System.currentTimeMillis() + ttl.toMillis(),
                ttl.toMillis(),
                hold.holdKey(),
                HoldRedisKey.holdMeta(""),
                hold.memberId().toString()));
        for (final Long seatId : hold.seatIds()) {
            keys.add(HoldRedisKey.hold(hold.performanceId(), seatId));
            args.add(seatId.toString());
        }
        try {
            final Long saved = redissonClient
                    .getScript(StringCodec.INSTANCE)
                    .eval(
                            RScript.Mode.READ_WRITE,
                            SAVE_IF_ABSENT_SCRIPT,
                            RScript.ReturnType.LONG,
                            keys,
                            args.toArray());
            if (saved == 1L) {
                return SaveResult.SAVED;
            }
            return saved == 2L ? SaveResult.HELD_BY_SAME_MEMBER : SaveResult.HELD_BY_OTHER;
        } catch (final RuntimeException e) {
            throw new IllegalStateException("hold Redis 저장에 실패했습니다.", e);
        }
    }

    @Override
    public void release(final Long performanceId, final String holdKey, final List<Long> seatIds) {
        final RSetCache<Long> holdSeatIndex = holdSeatIndex(performanceId);
        for (final Long seatId : seatIds.stream().distinct().toList()) {
            final RBucket<String> bucket = seatBucket(performanceId, seatId);
            if (holdKey.equals(bucket.get())) {
                holdSeatIndex.remove(seatId);
                bucket.delete();
            }
        }
        redissonClient
                .getBucket(HoldRedisKey.holdMeta(holdKey), StringCodec.INSTANCE)
                .delete();
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

    private RBucket<String> seatBucket(final Long performanceId, final Long seatId) {
        return redissonClient.getBucket(HoldRedisKey.hold(performanceId, seatId), StringCodec.INSTANCE);
    }

    private RSetCache<Long> holdSeatIndex(final Long performanceId) {
        return redissonClient.getSetCache(HoldRedisKey.holdSeatIndex(performanceId), LongCodec.INSTANCE);
    }
}
