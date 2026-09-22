/**
 * 좌석 선택·해제 use case와 그 조율이다.
 *
 * <p>{@code SeatSelectionCoordinator}는 선택 상태를 바꾸면서 선점 충돌을 확인하고 좌석 상태 이벤트를 발행한다 — 그 workflow의 결과를 책임지는 것이 선택 상태라
 * selection이 소유한다.
 */
@NullMarked
package com.ticket.booking.selection.usecase;

import org.jspecify.annotations.NullMarked;
