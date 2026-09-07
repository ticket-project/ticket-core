package com.ticket.show;

import java.time.LocalDateTime;

/**
 * 다른 module이 응답을 조합할 때 필요한 회차 표시값만 담은 불변 snapshot이다. show 표시값은
 * {@link ShowSummary}가 따로 담당한다 — 이 record의 {@code showId}로 이어 조회한다.
 */
public record PerformanceSummary(
        long performanceId,
        long showId,
        Long performanceNo,
        LocalDateTime startTime
) {
}
