package com.ticket.booking.infrastructure;

import com.ticket.booking.application.SeatStatusEvent;
import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.SeatStatusEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSeatStatusEventPublisher implements SeatStatusEventPublisher {

    private static final String SEAT_TOPIC_FORMAT = "/topic/performance/%d/seats";

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    @Override
    public void publish(final Long performanceId, final Long performanceSeatId, final SeatStatusAction action) {
        final SeatStatusEvent event =
                SeatStatusEvent.of(performanceId, performanceSeatId, action, LocalDateTime.now(clock));
        final String destination = String.format(SEAT_TOPIC_FORMAT, event.performanceId());
        messagingTemplate.convertAndSend(destination, event);
        log.debug("seat event published: action={}, perfId={}, performanceSeatId={}",
                event.action(), event.performanceId(), event.performanceSeatId());
    }
}
