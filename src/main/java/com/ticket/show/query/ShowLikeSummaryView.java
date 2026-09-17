package com.ticket.show.query;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

public record ShowLikeSummaryView(
        Long showId,
        @Nullable String title,
        @Nullable String image,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable String venue,
        LocalDateTime likedAt) {}
