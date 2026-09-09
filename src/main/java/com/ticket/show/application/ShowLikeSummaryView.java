package com.ticket.show.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShowLikeSummaryView(
        Long showId, String title, String image, LocalDate startDate, LocalDate endDate, String venue, LocalDateTime likedAt
) {}
