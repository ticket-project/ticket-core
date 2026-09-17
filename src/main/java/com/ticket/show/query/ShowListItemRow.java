package com.ticket.show.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.ticket.show.domain.show.SaleType;

/**
 * {@link ShowListQueryPort#findAllBySearch}의 조회 결과 한 행이다. {@code venueId}는 scalar 참조만 담는다 — venue
 * 표시값 조합(VenueLookupApi 호출)은 infrastructure가 아니라 {@code GetShowsUseCase}(application)가 한다.
 */
public record ShowListItemRow(
        Long id,
        @Nullable String title,
        @Nullable String subTitle,
        @Nullable String image,
        List<String> genreNames,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        long viewCount,
        SaleType displaySaleType,
        @Nullable LocalDateTime displaySaleStartsAt,
        @Nullable LocalDateTime displaySaleEndsAt,
        LocalDateTime createdAt,
        @Nullable Long venueId) {}
