package com.ticket.show.application.show.query.model;

import com.ticket.venue.Region;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShowOpeningSoonDetailView(
        Long id,
        String title,
        String subTitle,
        String image,
        String venue,
        Region region,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime saleStartDate,
        LocalDateTime saleEndDate,
        long viewCount
) {
}
