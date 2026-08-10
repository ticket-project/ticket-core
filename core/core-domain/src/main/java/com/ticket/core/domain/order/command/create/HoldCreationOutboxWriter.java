package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class HoldCreationOutboxWriter {

    private final HoldCreationOutboxRepository holdCreationOutboxRepository;

    public Long append(final HoldSnapshot snapshot, final LocalDateTime nextAttemptAt) {
        return holdCreationOutboxRepository.save(HoldCreationOutbox.create(snapshot, nextAttemptAt)).getId();
    }
}
