package com.ticket.show.query;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * {@link ShowListQuery#findSaleOpeningSoonPage}의 조회 결과 한 행이다. {@code venueId}는 scalar 참조만 담는다 —
 * venue 표시값 조합은 {@code GetSaleOpeningSoonShowsPageUseCase} (application)가 한다.
 */
public record SaleOpeningSoonDetailRow(
        Long id,
        @Nullable String title,
        @Nullable String subTitle,
        @Nullable String image,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable LocalDateTime displaySaleStartsAt,
        @Nullable LocalDateTime displaySaleEndsAt,
        long viewCount,
        @Nullable Long venueId) {}
