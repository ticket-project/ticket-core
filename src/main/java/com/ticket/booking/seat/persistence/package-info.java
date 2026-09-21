/**
 * PerformanceSeat의 JPA 저장·조회 구현이다. 저장/복원과 조회 계약({@code
 * booking.seat.domain.PerformanceSeatRepository})은 밖에 있다. 조회가 돌려주는 것은 entity이고, 응답 항목과 집계 결과는 그것을
 * 쓰는 use case가 소유한다({@code booking.seat.usecase}).
 */
@NullMarked
package com.ticket.booking.seat.persistence;

import org.jspecify.annotations.NullMarked;
