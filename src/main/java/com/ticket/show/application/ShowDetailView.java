package com.ticket.show.application;

import com.ticket.show.domain.SaleDisplayStatus;
import com.ticket.venue.Region;
import com.ticket.show.domain.SaleType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code likeCount}는 담지 않는다 — 찜 개수는 like module 소유라 {@link GetShowDetailUseCase}가
 * 별도로 조회해 응답에 합친다.
 *
 * <p>컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름
 * {@code bookingStatus}/{@code saleType}/{@code saleStartDate}/{@code saleEndDate}는
 * {@code ticket-fe}가 이미 쓰고 있어 {@link JsonProperty}로 그대로 고정한다.
 */
public record ShowDetailView(
        Long id, String title, String subTitle, String info, LocalDate startDate, LocalDate endDate,
        Integer runningMinutes, long viewCount,
        @JsonProperty("bookingStatus") SaleDisplayStatus saleDisplayStatus,
        @JsonProperty("saleType") SaleType displaySaleType,
        @JsonProperty("saleStartDate") LocalDateTime displaySaleStartsAt,
        @JsonProperty("saleEndDate") LocalDateTime displaySaleEndsAt,
        String image,
        GetShowDetailUseCase.VenueInfo venue, GetShowDetailUseCase.PerformerInfo performer,
        List<String> genreNames, GetShowDetailUseCase.PriceSummary priceSummary,
        List<GetShowDetailUseCase.PerformanceDateInfo> performanceDates
) {}
