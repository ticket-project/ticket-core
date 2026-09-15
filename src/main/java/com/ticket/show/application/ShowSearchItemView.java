package com.ticket.show.application;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.ticket.venue.api.Region;

public record ShowSearchItemView(
        Long id,
        @Nullable String title,
        @Nullable String image,
        @Nullable String venue,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable Region region,
        long viewCount) {}
