package com.ticket.catalog.application.show.query.model;

import java.time.LocalDateTime;

public record ShowOpeningSoonSummaryView(
        Long id,
        String title,
        String image,
        String venue,
        LocalDateTime saleStartDate
) {
}
