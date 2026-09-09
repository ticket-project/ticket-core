package com.ticket.show.application;

import com.ticket.show.application.usecase.GetShowsUseCase;

import com.ticket.show.application.port.ShowListQueryPort;

import com.ticket.show.domain.SaleType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link ShowListQueryPort#findAllBySearch}의 조회 결과 한 행이다. {@code venueId}는
 * scalar 참조만 담는다 — venue 표시값 조합(VenueLookup 호출)은 infrastructure가 아니라
 * {@code GetShowsUseCase}(application)가 한다.
 */
public record ShowListItemRow(
        Long id,
        String title,
        String subTitle,
        String image,
        List<String> genreNames,
        LocalDate startDate,
        LocalDate endDate,
        long viewCount,
        SaleType displaySaleType,
        LocalDateTime displaySaleStartsAt,
        LocalDateTime displaySaleEndsAt,
        LocalDateTime createdAt,
        Long venueId
) {
}
