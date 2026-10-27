package com.ticket.booking.websocket;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.ticket.booking.seat.port.SeatStatusEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 좌석 상태 알림 relay. 좌석 상태를 바꾸는 Lua 스크립트가 바꾼 그 자리에서 채널 하나({@link #CHANNEL})에 {@code PUBLISH}하고, 모든 인스턴스가 그 채널을 구독해 자기
 * STOMP 사용자에게 보낸다. 발행한 인스턴스도 자기 사용자에게 직접 보내지 않고 구독으로 받는다 — 전달 경로가 하나라 중복도, 경로 사이의 순서 차이도 없다(스케일 아웃 결정 D2-1).
 *
 * <p>Redis가 스크립트를 하나씩 실행하고 {@code PUBLISH}를 처리한 순서대로 보내며, 구독은 단일 스레드로 받으므로({@link SeatStatusRelayConfig}) 알림 순서가 좌석 상태가
 * 바뀐 순서와 같다. 메시지는 {@link SeatStatusEvent} JSON 그대로라 사용자에게 가는 payload와 destination은 예전과 같다.
 *
 * <p>Redis pub/sub은 최대 한 번 전달이다. 구독이 끊긴 동안의 알림은 다시 오지 않는다. 사용자가 화면을 새로 불러오면 맞춰진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatStatusRelay implements MessageListener {
    public static final String CHANNEL = "seat:status:relay";
    private static final String SEAT_TOPIC_FORMAT = "/topic/performance/%d/seats";

    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    /** 스크립트가 {@code PUBLISH}할 메시지. 상태를 바꾸는 쪽이 알림이 나갈지 모르는 채로 미리 만들어 넘긴다. */
    public String encode(final SeatStatusEvent event) {
        return jsonMapper.writeValueAsString(event);
    }

    @Override
    public void onMessage(final Message message, final byte @Nullable [] pattern) {
        final SeatStatusEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), SeatStatusEvent.class);
        } catch (final JacksonException e) {
            log.warn("좌석 상태 relay 메시지를 읽지 못해 버립니다.", e);
            return;
        }
        // 그 회차를 구독한 사용자가 이 인스턴스에 없으면 simple broker가 버린다.
        messagingTemplate.convertAndSend(String.format(SEAT_TOPIC_FORMAT, event.performanceId()), event);
        log.debug(
                "seat event relayed: action={}, perfId={}, performanceSeatId={}",
                event.action(),
                event.performanceId(),
                event.performanceSeatId());
    }
}
