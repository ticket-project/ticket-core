package com.ticket.show.api;

/**
 * 회차 요약(공연 제목·공연장 지역·시작 시각) 공개 계약이다. booking의 점유 좌석 조회가 좌석 화면이 함께 그리는 요약을 같은 응답에 담으려고 쓴다 — 요약을 따로 부르면 예매 사용자마다 HTTP 요청이
 * 하나 더 든다(gatling-test capacity-log 2026-10-19).
 */
public interface PerformanceSummaryApi {
    /** 존재하지 않는 회차 ID는 공통 오류({@code com.ticket.shared.exception.NotFoundException})로 알린다. */
    PerformanceSummarySnapshot getPerformanceSummary(long performanceId);
}
