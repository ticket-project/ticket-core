package com.ticket.show.catalog.application;

import com.ticket.venue.Region;
import com.ticket.show.catalog.domain.SaleType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름
 * {@code saleType}/{@code saleStartDate}/{@code saleEndDate}는 그대로 고정한다.
 */
public record ShowListItemView(
        Long id,
        String title,
        String subTitle,
        String image,
        List<String> genreNames,
        LocalDate startDate,
        LocalDate endDate,
        long viewCount,
        @JsonProperty("saleType") SaleType displaySaleType,
        @JsonProperty("saleStartDate") LocalDateTime displaySaleStartsAt,
        @JsonProperty("saleEndDate") LocalDateTime displaySaleEndsAt,
        LocalDateTime createdAt,
        Region region,
        String venue
) {
}
