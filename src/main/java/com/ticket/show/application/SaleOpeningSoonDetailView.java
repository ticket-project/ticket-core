package com.ticket.show.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.venue.Region;

/**
 * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code saleStartDate}/{@code
 * saleEndDate}는 그대로 고정한다.
 */
public record SaleOpeningSoonDetailView(
        Long id,
        String title,
        String subTitle,
        String image,
        String venue,
        Region region,
        LocalDate startDate,
        LocalDate endDate,
        @JsonProperty("saleStartDate") LocalDateTime displaySaleStartsAt,
        @JsonProperty("saleEndDate") LocalDateTime displaySaleEndsAt,
        long viewCount) {}
