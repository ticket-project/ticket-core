/**
 * PerformanceSeat의 저장 adapter와 local DB 조회 Repository 구현이다. 조회가 돌려주는 것은 entity이고, 응답 항목과 집계 결과는 그것을
 * 쓰는 use case가 소유한다({@code booking.seat.usecase}).
 */
@NullMarked
package com.ticket.booking.seat.persistence;

import org.jspecify.annotations.NullMarked;
