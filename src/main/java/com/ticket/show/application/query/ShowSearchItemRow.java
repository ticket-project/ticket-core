package com.ticket.show.application.query;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

import com.ticket.show.application.port.ShowListQueryPort;

/**
 * {@link ShowListQueryPort#searchShows}의 조회 결과 한 행이다. {@code venueId}는 scalar 참조만 담는다 — venue 표시값
 * 조합은 {@code SearchShowsUseCase}(application)가 한다.
 */
public record ShowSearchItemRow(
        Long id,
        @Nullable String title,
        @Nullable String image,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        long viewCount,
        @Nullable Long venueId) {}
