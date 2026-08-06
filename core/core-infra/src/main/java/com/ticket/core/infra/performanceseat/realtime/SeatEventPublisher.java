package com.ticket.core.infra.performanceseat.realtime;

import com.ticket.core.domain.performanceseat.support.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatStatusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatEventPublisher implements SeatStatusEventPublisher {

    private static final String SEAT_TOPIC_FORMAT = "/topic/performance/%d/seats";

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    @Override
    public void publish(final Long performanceId, final Long seatId, final SeatStatusMessage.SeatAction action) {
        publish(SeatStatusMessage.of(performanceId, seatId, action, LocalDateTime.now(clock)));
    }

    @Override
    public void publish(final SeatStatusMessage message) {
        final String destination = String.format(SEAT_TOPIC_FORMAT, message.performanceId());
        messagingTemplate.convertAndSend(destination, message);
        log.debug("seat event published: action={}, perfId={}, seatId={}",
                message.action(), message.performanceId(), message.seatId());
    }
}
