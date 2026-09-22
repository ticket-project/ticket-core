package com.ticket.booking.seat.domain;

/**
 * 회차 좌석 한 건의 식별자와 판매 상태다. 담고 있는 값이 둘 다 공연 좌석의 것이라 seat가 소유한다 — 옛
 * {@code selection.domain.SeatSelectionAvailabilitySnapshot}은 좌석 조회 계약이 selection을 참조하게 만들었다.
 *
 * <p>예매 가능 시각은 여기 담지 않는다. 회차 정책은 캐시할 수 있고 좌석 상태는 결제 확정으로 런타임에 변하므로, 두 값을 한 쿼리로 묶으면 정책만 캐시할 수 없게 된다.
 */
public record PerformanceSeatStateSnapshot(Long performanceSeatId, PerformanceSeatState state) {}
