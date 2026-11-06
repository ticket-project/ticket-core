package com.ticket.booking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.websocket.SeatStatusRelay.SeatStatusBatch;
import com.ticket.booking.websocket.SeatStatusRelay.SeatStatusChange;

import tools.jackson.databind.json.JsonMapper;

/** 좌석 알림을 회차별로 모아 한 메시지로 보내고, 같은 좌석은 마지막 상태만 남기는지 본다. */
@SuppressWarnings("NonAsciiCharacters")
class SeatStatusRelayTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final SeatStatusRelay relay =
            new SeatStatusRelay(messagingTemplate, JsonMapper.builder().build());

    @Test
    void 받은_알림은_flush_전까지_보내지_않는다() {
        receive(event(10L, 1L, SeatStatusAction.SELECTED));

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void 같은_회차의_알림은_한_메시지로_보내고_같은_좌석은_마지막_상태만_남긴다() {
        receive(event(10L, 1L, SeatStatusAction.SELECTED));
        receive(event(10L, 2L, SeatStatusAction.SELECTED));
        receive(event(10L, 1L, SeatStatusAction.DESELECTED));

        relay.flush();

        final ArgumentCaptor<SeatStatusBatch> captor = ArgumentCaptor.forClass(SeatStatusBatch.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/performance/10/seats"), captor.capture());
        // 좌석 순서는 그 간격에서 마지막으로 바뀐 순서다.
        assertThat(captor.getValue())
                .isEqualTo(new SeatStatusBatch(
                        10L,
                        List.of(
                                new SeatStatusChange(1002L, 2L, SeatStatusAction.SELECTED),
                                new SeatStatusChange(1001L, 1L, SeatStatusAction.DESELECTED))));
    }

    @Test
    void 회차마다_따로_보내고_보낸_뒤에는_비운다() {
        receive(event(10L, 1L, SeatStatusAction.HELD));
        receive(event(20L, 1L, SeatStatusAction.RELEASED));

        relay.flush();
        relay.flush();

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/performance/10/seats"), any(Object.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/performance/20/seats"), any(Object.class));
    }

    @Test
    void 읽지_못한_메시지는_버리고_계속한다() {
        relay.onMessage(
                new DefaultMessage(
                        SeatStatusRelay.CHANNEL.getBytes(StandardCharsets.UTF_8),
                        "not-json".getBytes(StandardCharsets.UTF_8)),
                null);
        receive(event(10L, 1L, SeatStatusAction.SELECTED));

        relay.flush();

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/performance/10/seats"), any(Object.class));
    }

    @Test
    void 멈추면_남은_묶음을_보낸다() {
        relay.start();
        receive(event(10L, 1L, SeatStatusAction.SELECTED));

        relay.stop();

        assertThat(relay.isRunning()).isFalse();
        verify(messagingTemplate).convertAndSend(eq("/topic/performance/10/seats"), any(Object.class));
    }

    private void receive(final SeatStatusEvent event) {
        relay.onMessage(
                new DefaultMessage(
                        SeatStatusRelay.CHANNEL.getBytes(StandardCharsets.UTF_8),
                        relay.encode(event).getBytes(StandardCharsets.UTF_8)),
                null);
    }

    private SeatStatusEvent event(final long performanceId, final long seatId, final SeatStatusAction action) {
        return new SeatStatusEvent(performanceId, 1000L + seatId, seatId, action, NOW);
    }
}
