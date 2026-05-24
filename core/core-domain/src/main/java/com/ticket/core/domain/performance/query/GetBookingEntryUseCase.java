package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.model.Performance;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetBookingEntryUseCase {

    private static final String TICKETING_URL_TEMPLATE = "/booking/seat?performanceId=%d";
    private static final String QUEUE_ENTER_URL_TEMPLATE = "/api/v1/queue/performances/%d/enter";

    private final PerformanceFinder performanceFinder;
    private final Clock clock;

    public Output execute(final Input input) {
        Performance performance = performanceFinder.findById(input.performanceId());
        if (performance.requiresQueueAt(LocalDateTime.now(clock))) {
            return new Output(
                    EntryType.QUEUE,
                    true,
                    null,
                    QUEUE_ENTER_URL_TEMPLATE.formatted(input.performanceId())
            );
        }
        return new Output(
                EntryType.DIRECT,
                false,
                TICKETING_URL_TEMPLATE.formatted(input.performanceId()),
                null
        );
    }

    public record Input(Long performanceId) {
    }

    public record Output(
            EntryType entryType,
            boolean queueRequired,
            String redirectUrl,
            String queueEnterUrl
    ) {
    }

    public enum EntryType {
        DIRECT,
        QUEUE
    }
}