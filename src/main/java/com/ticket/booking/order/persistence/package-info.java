/**
 * Order aggregate의 저장 adapter다.
 *
 * <p>저장/복원 계약({@code booking.order.domain.OrderRepository})은 밖에 있다 — 여기에는 그 계약을 만족시키는 저장 기술(Spring
 * Data JPA 인터페이스, 락, fetch 전략)만 둔다.
 */
@NullMarked
package com.ticket.booking.order.persistence;

import org.jspecify.annotations.NullMarked;
