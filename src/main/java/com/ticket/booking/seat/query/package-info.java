/**
 * 좌석 조회의 응답 전용 값만 둔다. 조회 구현은 {@code booking.seat.persistence}가 갖고 {@code PerformanceSeat} 엔티티를
 * 돌려주며, 응답 항목은 각 use case의 중첩 record가 갖는다.
 */
@NullMarked
package com.ticket.booking.seat.query;

import org.jspecify.annotations.NullMarked;
