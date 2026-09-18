package com.ticket.show.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.usecase.GetShowDetailUseCase;

/**
 * {@code likeCount}는 담지 않는다 — 찜 개수는 like module 소유라 {@link GetShowDetailUseCase}가 별도로 조회해 응답에 합친다.
 *
 * <p>{@code venueId}는 scalar 참조만 담는다 — venue 표시값 조합(VenueLookupApi 호출)은 infrastructure가 아니라 {@link
 * GetShowDetailUseCase}가 한다. 조회의 역할은 show 자기 DB를 읽는 것까지다.
 *
 * <p>컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code bookingStatus}/{@code
 * saleType}/{@code saleStartDate}/{@code saleEndDate}는 {@code ticket-fe}가 이미 쓰고 있어 {@link
 * JsonProperty}로 그대로 고정한다.
 */
public record ShowDetailView(
        Long id,
        @Nullable String title,
        @Nullable String subTitle,
        String info,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        @Nullable Integer runningMinutes,
        long viewCount,
        @JsonProperty("bookingStatus") SaleDisplayStatus saleDisplayStatus,
        @JsonProperty("saleType") SaleType displaySaleType,
        @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt,
        @JsonProperty("saleEndDate") @Nullable LocalDateTime displaySaleEndsAt,
        @Nullable String image,
        @Nullable Long venueId,
        @Nullable PerformerInfo performer,
        List<String> genreNames,
        List<ShowGradeView> grades,
        @Nullable PriceSummary priceSummary,
        List<PerformanceDateInfo> performanceDates) {}
