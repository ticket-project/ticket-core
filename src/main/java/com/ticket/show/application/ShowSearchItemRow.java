package com.ticket.show.application;

import java.time.LocalDate;

import com.ticket.show.application.port.ShowListQueryPort;

/**
 * {@link ShowListQueryPort#searchShows}의 조회 결과 한 행이다. {@code venueId}는 scalar 참조만 담는다 — venue 표시값
 * 조합은 {@code SearchShowsUseCase}(application)가 한다.
 */
public record ShowSearchItemRow(
        Long id,
        String title,
        String image,
        LocalDate startDate,
        LocalDate endDate,
        long viewCount,
        Long venueId) {}
