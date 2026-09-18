package com.ticket.show.query;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * {@code ShowQueryRepository#findLatestShows}의 조회 결과 한 행이다. {@code venueId}는 scalar 참조만 담는다 — venue
 * 표시값 조합은 {@code GetLatestShowsUseCase}(application)가 한다.
 */
public record LatestShowRow(
        Long id,
        @Nullable String title,
        @Nullable String image,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable Long venueId,
        LocalDateTime createdAt) {}
