package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.model.Hold;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class HoldCreationOutboxWriter {

    private final HoldCreationOutboxRepository holdCreationOutboxRepository;

    public Long append(final Hold hold, final LocalDateTime nextAttemptAt) {
        return holdCreationOutboxRepository.save(HoldCreationOutbox.create(hold, nextAttemptAt)).getId();
    }
}
