package com.ticket.booking.hold.persistence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.selection.persistence.SeatSelectionRedisKey;
import com.ticket.booking.websocket.SeatStatusRelay;

import lombok.RequiredArgsConstructor;

/**
 * 선점의 Redis 저장. 좌석 상태를 바꾸는 일은 모두 스크립트 하나에서 확인·변경·발행한다 — Redis가 스크립트를 하나씩 실행하므로 좌석 락 없이도 선택과 선점이 엇갈리지 않고, 알림 순서가 상태가 바뀐
 * 순서와 같다(좌석 락 재설계 C안). 선점 키와 선택 키, 두 인덱스는 같은 {@code {perf:N}} hash tag다.
 *
 * <p>회차별 점유 인덱스는 일반 Sorted Set이다(값 seatId, 점수 Redis {@code TIME} 기준 만료 시각 ms). 만료 항목은 저장할 때 지우고 읽을 때 점수로 거른다. 예전
 * {@code RSetCache}도 같은 모양이라 배포 때 남아 있는 인덱스를 그대로 읽는다.
 *
 * <p>ponytail: 메타 키({@code hold:key:...})에는 회차 hash tag가 없어 Redis Cluster에서는 좌석 키와 다른 slot이라 한 스크립트에 넣을 수 없다. 저장 스크립트는
 * KEYS에 선언하지 않은 다른 hold의 메타 키({@code ARGV[3] .. holder})도 읽는다. 지금은 Redis 한 대다. Cluster로 가면 메타 키에 회차 hash tag를 붙이고 미선언 키
 * 접근도 없애야 한다.
 */
@Component
@RequiredArgsConstructor
public class RedissonHoldStore implements HoldStore {
    /**
     * KEYS: 1 회차 점유 인덱스, 2 메타 키, 3..2+n 좌석 키, 3+n..2+2n 같은 좌석의 선택 키. ARGV: 1 TTL(ms), 2 hold key, 3 메타 키 접두사, 4 회원 ID,
     * 5 n, 6..5+n seatId. 반환값은 1 저장, 3 본인이 선택 중이 아닌 좌석이 있음, 2 이미 잡힌 좌석이 같은 회원의 선점, 0 그 밖에 이미 잡힌 좌석이 있음이다. 메타 키의 값은 선점한
     * 회원 ID다. Redis는 스크립트 중간 오류에서 앞선 쓰기를 되돌리지 않으므로, 실패할 수 있는 확인(인덱스 자료형)은 쓰기 전에 끝낸다.
     */
    private static final String SAVE_IF_ABSENT_SCRIPT = """
            local indexType = redis.call('type', KEYS[1])['ok']
            if indexType ~= 'none' and indexType ~= 'zset' then
                return redis.error_reply('WRONGTYPE hold seat index is ' .. indexType)
            end
            local n = tonumber(ARGV[5])
            for i = 1, n do
                if redis.call('get', KEYS[2 + n + i]) ~= ARGV[4] then
                    return 3
                end
            end
            local result = 1
            for i = 1, n do
                local holder = redis.call('get', KEYS[2 + i])
                if holder then
                    if redis.call('get', ARGV[3] .. holder) == ARGV[4] then
                        return 2
                    end
                    result = 0
                end
            end
            if result == 0 then
                return 0
            end
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            local expiresAt = nowMillis + tonumber(ARGV[1])
            redis.call('zremrangebyscore', KEYS[1], '-inf', nowMillis)
            for i = 1, n do
                redis.call('set', KEYS[2 + i], ARGV[2], 'PX', ARGV[1])
                redis.call('zadd', KEYS[1], expiresAt, ARGV[5 + i])
            end
            local latest = redis.call('zrevrange', KEYS[1], 0, 0, 'WITHSCORES')
            redis.call('pexpireat', KEYS[1], latest[2])
            redis.call('set', KEYS[2], ARGV[4], 'PX', ARGV[1])
            return 1
            """;

    /**
     * KEYS: 1 회차 선택 인덱스, 2 회원 선택 인덱스, 3..2+n 좌석 키, 3+n..2+2n 같은 좌석의 선택 키. ARGV: 1 hold key, 2 회원 ID, 3 채널, 4 n, 5..4+n
     * seatId, 5+n..4+2n 같은 좌석의 HELD 메시지. 좌석이 하나라도 이 hold가 아니면 0. 아니면 이 회원의 선택을 지우고 좌석마다 발행한 뒤 1.
     */
    private static final String CONFIRM_HELD_SCRIPT = """
            local n = tonumber(ARGV[4])
            for i = 1, n do
                if redis.call('get', KEYS[2 + i]) ~= ARGV[1] then
                    return 0
                end
            end
            for i = 1, n do
                if redis.call('get', KEYS[2 + n + i]) == ARGV[2] then
                    redis.call('del', KEYS[2 + n + i])
                    redis.call('zrem', KEYS[1], ARGV[4 + i])
                    redis.call('zrem', KEYS[2], ARGV[4 + i])
                end
                redis.call('publish', ARGV[3], ARGV[4 + n + i])
            end
            return 1
            """;

    /**
     * KEYS: 1 회차 점유 인덱스, 2 메타 키, 3..2+n 좌석 키, 3+n..2+2n 같은 좌석의 선택 키. ARGV: 1 hold key, 2 채널, 3 n, 4..3+n seatId,
     * 4+n..3+2n 같은 좌석의 RELEASED 메시지(빈 문자열이면 발행하지 않음). 좌석 키가 이 hold일 때만 지운다(compare-and-delete). 지운 뒤 선택도 선점도 없는 좌석이면
     * 발행한다.
     */
    private static final String RELEASE_SCRIPT = """
            local n = tonumber(ARGV[3])
            for i = 1, n do
                if redis.call('get', KEYS[2 + i]) == ARGV[1] then
                    redis.call('del', KEYS[2 + i])
                    redis.call('zrem', KEYS[1], ARGV[3 + i])
                end
            end
            redis.call('del', KEYS[2])
            for i = 1, n do
                local message = ARGV[3 + n + i]
                if message ~= '' and redis.call('exists', KEYS[2 + i]) == 0
                        and redis.call('exists', KEYS[2 + n + i]) == 0 then
                    redis.call('publish', ARGV[2], message)
                end
            end
            return 1
            """;

    private static final String READ_ACTIVE_SEAT_IDS_SCRIPT = """
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            return redis.call('zrangebyscore', KEYS[1], '(' .. nowMillis, '+inf')
            """;

    private final RedissonClient redissonClient;
    private final SeatStatusRelay seatStatusRelay;

    @Override
    public SaveResult saveIfAbsent(final Hold hold, final Duration ttl) {
        final List<Long> seatIds = hold.seatIds();
        final List<Object> keys = new ArrayList<>(
                List.of(HoldRedisKey.holdSeatIndex(hold.performanceId()), HoldRedisKey.holdMeta(hold.holdKey())));
        keys.addAll(seatKeys(hold.performanceId(), seatIds));
        final List<Object> args = new ArrayList<>(List.of(
                ttl.toMillis(),
                hold.holdKey(),
                HoldRedisKey.holdMeta(""),
                hold.memberId().toString(),
                seatIds.size()));
        seatIds.forEach(seatId -> args.add(seatId.toString()));
        try {
            final Long saved = eval(SAVE_IF_ABSENT_SCRIPT, keys, args);
            return switch (saved.intValue()) {
                case 1 -> SaveResult.SAVED;
                case 2 -> SaveResult.HELD_BY_SAME_MEMBER;
                case 3 -> SaveResult.NOT_SELECTED;
                default -> SaveResult.HELD_BY_OTHER;
            };
        } catch (final RuntimeException e) {
            throw new IllegalStateException("hold Redis 저장에 실패했습니다.", e);
        }
    }

    @Override
    public boolean confirmHeld(
            final Long performanceId, final String holdKey, final Long memberId, final List<SeatStatusEvent> held) {
        final List<Long> seatIds = held.stream().map(SeatStatusEvent::seatId).toList();
        final String memberKey = memberId.toString();
        final List<Object> keys = new ArrayList<>(List.of(
                SeatSelectionRedisKey.selectSeatIndex(performanceId),
                SeatSelectionRedisKey.selectMemberIndex(performanceId, memberKey)));
        keys.addAll(seatKeys(performanceId, seatIds));
        final List<Object> args = new ArrayList<>(List.of(holdKey, memberKey, SeatStatusRelay.CHANNEL, seatIds.size()));
        seatIds.forEach(seatId -> args.add(seatId.toString()));
        held.forEach(event -> args.add(seatStatusRelay.encode(event)));
        return eval(CONFIRM_HELD_SCRIPT, keys, args) == 1L;
    }

    @Override
    public void release(final Long performanceId, final String holdKey, final List<SeatStatusEvent> released) {
        release(
                performanceId,
                holdKey,
                released.stream().map(SeatStatusEvent::seatId).toList(),
                released.stream().map(seatStatusRelay::encode).toList());
    }

    @Override
    public void releaseWithoutNotice(final Long performanceId, final String holdKey, final List<Long> seatIds) {
        release(
                performanceId,
                holdKey,
                seatIds,
                seatIds.stream().map(seatId -> "").toList());
    }

    @Override
    public Set<Long> getHoldingSeatIds(final Long performanceId) {
        final List<String> seatIds = redissonClient
                .getScript(StringCodec.INSTANCE)
                .eval(
                        RScript.Mode.READ_ONLY,
                        READ_ACTIVE_SEAT_IDS_SCRIPT,
                        RScript.ReturnType.LIST,
                        List.<Object>of(HoldRedisKey.holdSeatIndex(performanceId)));
        final Set<Long> result = HashSet.newHashSet(seatIds.size());
        seatIds.forEach(seatId -> result.add(Long.valueOf(seatId)));
        return result;
    }

    private void release(
            final Long performanceId, final String holdKey, final List<Long> seatIds, final List<String> messages) {
        final List<Object> keys =
                new ArrayList<>(List.of(HoldRedisKey.holdSeatIndex(performanceId), HoldRedisKey.holdMeta(holdKey)));
        keys.addAll(seatKeys(performanceId, seatIds));
        final List<Object> args = new ArrayList<>(List.of(holdKey, SeatStatusRelay.CHANNEL, seatIds.size()));
        seatIds.forEach(seatId -> args.add(seatId.toString()));
        args.addAll(messages);
        eval(RELEASE_SCRIPT, keys, args);
    }

    /** 좌석 키 n개 뒤에 같은 순서의 선택 키 n개. 스크립트가 i번째 좌석의 두 키를 KEYS[2 + i], KEYS[2 + n + i]로 읽는다. */
    private List<Object> seatKeys(final Long performanceId, final List<Long> seatIds) {
        final List<Object> keys = new ArrayList<>(seatIds.size() * 2);
        seatIds.forEach(seatId -> keys.add(HoldRedisKey.hold(performanceId, seatId)));
        seatIds.forEach(seatId -> keys.add(SeatSelectionRedisKey.select(performanceId, seatId)));
        return keys;
    }

    private Long eval(final String script, final List<Object> keys, final List<Object> args) {
        return redissonClient
                .getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.LONG, keys, args.toArray());
    }
}
