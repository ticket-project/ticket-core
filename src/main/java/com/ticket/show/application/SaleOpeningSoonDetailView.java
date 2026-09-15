package com.ticket.show.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.venue.api.Region;

/**
 * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code saleStartDate}/{@code
 * saleEndDate}는 그대로 고정한다.
 */
public record SaleOpeningSoonDetailView(
        Long id,
        @Nullable String title,
        @Nullable String subTitle,
        @Nullable String image,
        @Nullable String venue,
        @Nullable Region region,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt,
        @JsonProperty("saleEndDate") @Nullable LocalDateTime displaySaleEndsAt,
        long viewCount) {}
