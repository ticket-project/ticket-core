package com.ticket.show.usecase;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

import com.ticket.show.exception.ShowNotFoundException;

/**
 * 회차와 그 공연에서 예매 화면 요약({@link GetPerformanceSummaryUseCase}), 주문 표시값({@link PerformanceSaleHeaderReader})과 seat-map
 * 배치({@link PerformanceSnapshotService})가 쓰는 값이다. {@code ShowQuerydslRepository#findPerformanceShow}가 한 번의 조인으로 채운다 —
 * 회차·공연을 따로 읽으면 예매 사용자 요청마다 DB를 두 번 다녀오고 공연의 소개 글 같은 큰 열까지 싣는다(gatling-test capacity-log 2026-10-19).
 *
 * <p>{@code venueId}가 null이면 회차의 공연이 없다. 회차와 공연 사이에는 FK가 없다.
 */
public record PerformanceShowRow(
        Long showId,
        LocalDateTime startTime,
        @Nullable String title,
        @Nullable Long venueId) {
    /** 공연이 없으면 회차·공연을 따로 읽던 때와 같은 {@link ShowNotFoundException}을 던진다. */
    public Long requireVenueId() {
        if (venueId == null) {
            throw new ShowNotFoundException(showId);
        }
        return venueId;
    }
}
