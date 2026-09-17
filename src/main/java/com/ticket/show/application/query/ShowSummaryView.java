package com.ticket.show.application.query;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

public record ShowSummaryView(
        Long id,
        @Nullable String title,
        @Nullable String image,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable String venue,
        LocalDateTime createdAt) {}
