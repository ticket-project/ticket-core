package com.ticket.show.catalog.application;

import java.time.LocalDateTime;

import com.ticket.show.catalog.application.port.ShowListQueryPort;

/**
 * {@link ShowListQueryPort#findSaleOpeningSoonSummaries}의 조회 결과 한 행이다. {@code venueId}는 scalar 참조만
 * 담는다 — venue 표시값 조합은 {@code GetSaleOpeningSoonShowsUseCase} (application)가 한다.
 */
public record SaleOpeningSoonSummaryRow(
        Long id, String title, String image, Long venueId, LocalDateTime displaySaleStartsAt) {}
