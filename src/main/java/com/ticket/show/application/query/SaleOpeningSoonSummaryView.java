package com.ticket.show.application.query;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code saleStartDate}는 그대로 고정한다. */
public record SaleOpeningSoonSummaryView(
        Long id,
        @Nullable String title,
        @Nullable String image,
        @Nullable String venue,
        @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt) {}
