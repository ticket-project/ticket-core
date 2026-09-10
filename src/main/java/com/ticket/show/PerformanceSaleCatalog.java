package com.ticket.show;

import java.util.Set;

/**
 * booking이 회차 판매 편성(PerformanceSeat 생성)과 주문 표시 snapshot 작성에 필요한 show 표시값을
 * 한 번에 조회하는 공개 계약이다.
 *
 * <p>존재하지 않는 회차 ID는 공통 오류({@code com.ticket.shared.exception.NotFoundException})로 알린다. 요청한
 * 좌석이 그 회차의 Venue에 속하지 않으면 {@link PerformanceSaleSnapshot#seatInfoBySeatId()}에서
 * 조용히 빠진다 — 그 사실을 어떤 오류로 다룰지는 호출하는 booking 쪽 유스케이스가 정한다.
 */
public interface PerformanceSaleCatalog {

    /**
     * @param seatIds 좌석 표시값을 함께 조회할 좌석 ID. 비어 있으면
     *                {@link PerformanceSaleSnapshot#seatInfoBySeatId()}도 빈 맵이다.
     */
    PerformanceSaleSnapshot getSaleSnapshot(long performanceId, Set<Long> seatIds);
}
