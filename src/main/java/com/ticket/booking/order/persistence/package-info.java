/**
 * Order aggregate의 JPA 저장 구현과 주문 조회 port의 Querydsl 구현이다.
 *
 * <p>저장/복원 계약({@code booking.order.domain.OrderRepository})과 조회 계약({@code booking.order.query})은 밖에
 * 있다 — 여기에는 그 계약을 만족시키는 저장 기술만 둔다.
 */
@NullMarked
package com.ticket.booking.order.persistence;

import org.jspecify.annotations.NullMarked;
