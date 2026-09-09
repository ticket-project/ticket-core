package com.ticket.show.application;

import com.ticket.show.application.usecase.GetSaleStartApproachingShowsUseCase;

import com.ticket.show.application.port.ShowListQueryPort;

import java.time.LocalDateTime;

/**
 * {@link ShowListQueryPort#findShowsSaleOpeningSoon}의 조회 결과 한 행이다. {@code venueId}는
 * scalar 참조만 담는다 — venue 표시값 조합은 {@code GetSaleStartApproachingShowsUseCase}
 * (application)가 한다.
 */
public record SaleOpeningSoonSummaryRow(
        Long id,
        String title,
        String image,
        Long venueId,
        LocalDateTime displaySaleStartsAt
) {
}
