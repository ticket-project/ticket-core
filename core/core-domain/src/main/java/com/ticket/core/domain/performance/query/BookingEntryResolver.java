package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.model.Performance;
import java.time.LocalDateTime;

public final class BookingEntryResolver {

    private static final String TICKETING_URL_TEMPLATE = "/booking/seat?performanceId=%d";
    private static final String QUEUE_ENTER_URL_TEMPLATE = "/api/v1/queue/performances/%d/enter";

    private BookingEntryResolver() {
    }

    public static Output resolve(final Long performanceId, final Performance performance, final LocalDateTime now) {
        if (performance.requiresQueueAt(now)) {
            return new Output(
                    EntryType.QUEUE,
                    true,
                    null,
                    QUEUE_ENTER_URL_TEMPLATE.formatted(performanceId)
            );
        }
        return new Output(
                EntryType.DIRECT,
                false,
                TICKETING_URL_TEMPLATE.formatted(performanceId),
                null
        );
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
