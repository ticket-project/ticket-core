/**
 * 판매 좌석 capability다 — 회차별 판매 좌석(PerformanceSeat)과 그 재고 상태, 좌석 배치도·잔여 조회를 소유한다.
 *
 * <p>사용자의 임시 좌석 선택 상태는 {@code booking.selection}이, 확정 전 선점은 {@code booking.hold}가 소유한다.
 */
@NullMarked
package com.ticket.booking.seat;

import org.jspecify.annotations.NullMarked;
