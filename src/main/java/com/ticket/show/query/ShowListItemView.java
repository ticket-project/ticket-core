package com.ticket.show.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.show.domain.show.SaleType;
import com.ticket.venue.api.Region;

/**
 * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code saleType}/{@code
 * saleStartDate}/{@code saleEndDate}는 그대로 고정한다.
 */
public record ShowListItemView(
        Long id,
        @Nullable String title,
        @Nullable String subTitle,
        @Nullable String image,
        List<String> genreNames,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        long viewCount,
        @JsonProperty("saleType") SaleType displaySaleType,
        @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt,
        @JsonProperty("saleEndDate") @Nullable LocalDateTime displaySaleEndsAt,
        LocalDateTime createdAt,
        @Nullable Region region,
        @Nullable String venue) {}
