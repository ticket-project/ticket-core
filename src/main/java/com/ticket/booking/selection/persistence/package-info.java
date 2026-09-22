/** 좌석 선택 상태의 Redis 구현이다. key 형식과 TTL, 만료 알림 처리가 함께 있다. 저장 계약({@code SeatSelectionStore})은 domain에 있다. */
@NullMarked
package com.ticket.booking.selection.persistence;

import org.jspecify.annotations.NullMarked;
