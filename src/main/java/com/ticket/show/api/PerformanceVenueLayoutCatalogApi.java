package com.ticket.show.api;

import java.util.Optional;

/**
 * booking이 정적 seat-map을 조합하는 데 필요한 show 쪽 조회를 모은 공개 계약이다. 회차 단위 Venue 배치·물리 Seat 좌표·
 * PerformanceGrade 표시값과, 공연(Show) 단위 진입점이 쓰는 대표 회차 식별자가 함께 있다.
 *
 * <p><b>대표 회차 조회를 따로 떼지 않는다.</b> 같은 조회({@code PerformanceRepository})를 같은 소비자 ({@code
 * booking.seat}의 seat-map use case)가 연달아 쓴다 — 공연 단위 요청은 대표 회차를 찾은 뒤 그 회차의 배치를 그린다. 계약을 둘로 나누면 구현
 * class 하나가 interface 둘을 달고 booking은 같은 목적의 의존을 둘 주입받게 된다.
 */
public interface PerformanceVenueLayoutCatalogApi {
    /**
     * 존재하지 않는 회차 ID는 공통 오류({@code com.ticket.shared.exception.NotFoundException})로 알린다. venue가 없는
     * show는 {@link PerformanceVenueLayout#seatLayoutBySeatId()}가 빈 맵이다.
     */
    PerformanceVenueLayout getVenueLayout(long performanceId);

    /** 공연(Show) 단위 조회가 seat-map을 그릴 때 기준으로 삼는 대표 회차다. 회차가 하나도 없으면 비어 있다. */
    Optional<Long> findRepresentativePerformanceId(long showId);
}
