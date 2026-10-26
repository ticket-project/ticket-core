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
 * relay 채널({@link RedisSeatStatusEventPublisher#CHANNEL})로 받은 좌석 상태 알림을 이 인스턴스의 STOMP 사용자에게 보낸다. 그 회차를 구독한 사용자가 없으면
 * simple broker가 버린다.
 *
 * <p>컨테이너가 단일 스레드 executor로 이 리스너를 부르므로({@link SeatStatusRelayConfig}) Redis가 보낸 순서대로 broker에 넣는다. 받는 JSON은
 * {@link SeatStatusEvent} 그대로라 사용자에게 가는 payload는 예전과 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatStatusRelaySubscriber implements MessageListener {
    private static final String SEAT_TOPIC_FORMAT = "/topic/performance/%d/seats";

    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void onMessage(final Message message, final byte @Nullable [] pattern) {
        final SeatStatusEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), SeatStatusEvent.class);
        } catch (final JacksonException e) {
            log.warn("좌석 상태 relay 메시지를 읽지 못해 버립니다.", e);
            return;
        }
        messagingTemplate.convertAndSend(String.format(SEAT_TOPIC_FORMAT, event.performanceId()), event);
        log.debug(
                "seat event relayed: action={}, perfId={}, performanceSeatId={}",
                event.action(),
                event.performanceId(),
                event.performanceSeatId());
    }
}
