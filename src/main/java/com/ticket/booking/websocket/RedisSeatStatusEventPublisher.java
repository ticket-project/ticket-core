package com.ticket.booking.websocket;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import com.ticket.booking.hold.persistence.HoldRedisKey;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.port.SeatStatusEventPublisher;
import com.ticket.booking.selection.persistence.SeatSelectionRedisKey;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 좌석 상태 알림을 Redis 채널 하나({@link #CHANNEL})에 발행한다. 모든 인스턴스가 이 채널을 구독해 자기 STOMP 사용자에게
 * 보낸다({@link SeatStatusRelaySubscriber}). 발행한 인스턴스도 자기 사용자에게 직접 보내지 않고 구독으로 받는다 — 전달 경로가 하나라 중복도, 경로 사이의 순서 차이도 없다(스케일
 * 아웃 결정 목록 D2-1).
 *
 * <p><b>발행은 Lua 스크립트 안에서 한다.</b> "비어 있으면 알린다"는 확인과 {@code PUBLISH}를 한 스크립트에서 하므로 그 사이에 다른 상태 변경이 끼지 못한다. 상태를 바꾸는 스크립트와
 * 발행 스크립트는 아직 따로이고, 둘 사이의 순서는 호출자가 잡은 좌석 락이 지킨다. 둘을 한 스크립트로 합치고 락을 걷어내는 일은 좌석 락 재설계(docs/plans/seat-lock-redesign.md)에서
 * 정한다.
 *
 * <p>Redis pub/sub은 at-most-once다. 구독이 끊긴 동안의 알림은 다시 오지 않는다. 사용자가 화면을 새로 불러오면 맞춰진다.
 */
@Component
@RequiredArgsConstructor
public class RedisSeatStatusEventPublisher implements SeatStatusEventPublisher {
    public static final String CHANNEL = "seat:status:relay";

    private static final String PUBLISH_SCRIPT = "return redis.call('publish', ARGV[1], ARGV[2])";

    /** KEYS: 1 좌석 선택 키, 2 좌석 선점 키. 둘 다 없을 때만 발행한다. 만료된 키는 EXISTS가 0을 돌려준다. */
    private static final String PUBLISH_IF_FREE_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 1 or redis.call('exists', KEYS[2]) == 1 then
                return 0
            end
            return redis.call('publish', ARGV[1], ARGV[2])
            """;

    private final RedissonClient redissonClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    public void publish(
            final Long performanceId,
            final @Nullable Long performanceSeatId,
            final Long seatId,
            final SeatStatusAction action) {
        eval(PUBLISH_SCRIPT, List.of(), event(performanceId, performanceSeatId, seatId, action));
    }

    @Override
    public void publishIfFree(
            final Long performanceId,
            final @Nullable Long performanceSeatId,
            final Long seatId,
            final SeatStatusAction action) {
        eval(
                PUBLISH_IF_FREE_SCRIPT,
                List.of(SeatSelectionRedisKey.select(performanceId, seatId), HoldRedisKey.hold(performanceId, seatId)),
                event(performanceId, performanceSeatId, seatId, action));
    }

    private SeatStatusEvent event(
            final Long performanceId,
            final @Nullable Long performanceSeatId,
            final Long seatId,
            final SeatStatusAction action) {
        return new SeatStatusEvent(performanceId, performanceSeatId, seatId, action, LocalDateTime.now(clock));
    }

    private void eval(final String script, final List<Object> keys, final SeatStatusEvent event) {
        redissonClient
                .getScript(StringCodec.INSTANCE)
                .eval(
                        RScript.Mode.READ_WRITE,
                        script,
                        RScript.ReturnType.LONG,
                        keys,
                        CHANNEL,
                        jsonMapper.writeValueAsString(event));
    }
}
