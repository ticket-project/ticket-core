package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.model.HoldReleaseReason;
import com.ticket.core.domain.order.OrderTerminationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class HoldReleaseOutboxWriter {

    private final HoldReleaseOutboxRepository holdReleaseOutboxRepository;
    private final Clock clock;

    public Long append(final OrderTerminationResult result, final HoldReleaseReason reason) {
        final HoldReleaseOutbox outbox = holdReleaseOutboxRepository.save(HoldReleaseOutbox.create(
                result.performanceId(),
                result.holdKey(),
                result.seatIds(),
                LocalDateTime.now(clock),
                reason
        ));
        return outbox.getId();
    }
}
