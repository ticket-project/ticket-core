package com.ticket.show;

import java.util.Optional;

/** 공연 단위의 기존 조회 계약을 지원하기 위해 대표 회차 식별자를 제공한다. */
public interface ShowPerformanceLookup {
    Optional<Long> findRepresentativePerformanceId(long showId);
}
