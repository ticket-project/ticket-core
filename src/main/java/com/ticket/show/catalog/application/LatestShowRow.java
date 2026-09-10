package com.ticket.show.catalog.application;

import com.ticket.show.catalog.application.usecase.GetLatestShowsUseCase;

import com.ticket.show.catalog.application.port.ShowListQueryPort;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@link ShowListQueryPort#findLatestShows}의 조회 결과 한 행이다. {@code venueId}는
 * scalar 참조만 담는다 — venue 표시값 조합은 {@code GetLatestShowsUseCase}(application)가 한다.
 */
public record LatestShowRow(
        Long id,
        String title,
        String image,
        LocalDate startDate,
        LocalDate endDate,
        Long venueId,
        LocalDateTime createdAt
) {
}
