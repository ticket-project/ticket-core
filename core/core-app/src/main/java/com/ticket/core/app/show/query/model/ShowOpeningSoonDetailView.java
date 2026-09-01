package com.ticket.core.app.show.query.model;

import com.ticket.core.domain.show.model.Region;

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
