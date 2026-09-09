package com.ticket.show.application;

import java.time.LocalDate;

/**
 * showId 집합으로 공연 표시값을 배치 조회하는 내부 포트({@code ShowSummaryBatchReadRepository})의
 * 조회 결과 한 행이다. 내 찜 목록처럼 show 내부의 다른 use case가 자기 show 데이터를 조회할 때
 * 쓰는 내부 타입이다 — 다른 module에는 노출하지 않는다. venue가 없는 show는 {@code venueName}이
 * {@code null}이다.
 */
public record ShowSummaryRow(
        long showId,
        String title,
        String image,
        LocalDate startDate,
        LocalDate endDate,
        String venueName
) {
}
