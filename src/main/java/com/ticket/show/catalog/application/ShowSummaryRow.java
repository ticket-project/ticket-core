package com.ticket.show.catalog.application;

import com.ticket.show.catalog.application.usecase.GetMyShowLikesUseCase;

import com.ticket.show.catalog.application.port.ShowSummaryBatchQueryPort;

import java.time.LocalDate;

/**
 * showId 집합으로 공연 표시값을 배치 조회하는 내부 포트({@code ShowSummaryBatchQueryPort})의
 * 조회 결과 한 행이다. 내 찜 목록처럼 show 내부의 다른 use case가 자기 show 데이터를 조회할 때
 * 쓰는 내부 타입이다 — 다른 module에는 노출하지 않는다. {@code venueId}는 scalar 참조만 담는다 —
 * venue 표시값 조합은 {@code GetMyShowLikesUseCase}(application)가 한다.
 */
public record ShowSummaryRow(
        long showId,
        String title,
        String image,
        LocalDate startDate,
        LocalDate endDate,
        Long venueId
) {
}
