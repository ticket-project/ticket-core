package com.ticket.core.app.show.query.model;

import com.ticket.core.domain.show.model.Region;

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
