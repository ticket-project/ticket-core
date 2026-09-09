package com.ticket.show.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름
 * {@code saleStartDate}는 그대로 고정한다.
 */
public record ShowOpeningSoonSummaryView(
        Long id,
        String title,
        String image,
        String venue,
        @JsonProperty("saleStartDate") LocalDateTime displaySaleStartsAt
) {
}
