package com.ticket.show.application.show.query.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShowSummaryView(
        Long id,
        String title,
        String image,
        LocalDate startDate,
        LocalDate endDate,
        String venue,
        LocalDateTime createdAt
) {
}
