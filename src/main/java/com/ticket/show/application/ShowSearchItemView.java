package com.ticket.show.application;

import java.time.LocalDate;

import com.ticket.venue.Region;

public record ShowSearchItemView(
        Long id,
        String title,
        String image,
        String venue,
        LocalDate startDate,
        LocalDate endDate,
        Region region,
        long viewCount) {}
