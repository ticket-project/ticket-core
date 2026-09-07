package com.ticket.show.application.show.query.model;

import com.ticket.show.domain.show.Region;

import java.time.LocalDate;

public record ShowSearchItemView(
        Long id,
        String title,
        String image,
        String venue,
        LocalDate startDate,
        LocalDate endDate,
        Region region,
        long viewCount
) {
}
