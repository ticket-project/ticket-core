package com.ticket.show.application;

import com.ticket.venue.Region;

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
