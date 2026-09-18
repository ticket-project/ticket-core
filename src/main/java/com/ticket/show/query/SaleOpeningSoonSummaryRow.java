package com.ticket.show.query;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * {@link ShowListQuery#findSaleOpeningSoonSummaries}의 조회 결과 한 행이다. {@code venueId}는 scalar 참조만 담는다
 * — venue 표시값 조합은 {@code GetSaleOpeningSoonShowsUseCase} (application)가 한다.
 */
public record SaleOpeningSoonSummaryRow(
        Long id,
        @Nullable String title,
        @Nullable String image,
        @Nullable Long venueId,
        @Nullable LocalDateTime displaySaleStartsAt) {}
