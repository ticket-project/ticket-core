/**
 * Order aggregate의 JPA 저장 구현이다.
 *
 * <p>저장/복원 계약({@code booking.order.domain.OrderRepository})은 밖에 있다 — 여기에는 그 계약을 만족시키는 저장 기술만 둔다. 주문
 * 조회는 aggregate 복원이 아니라 읽기 모델 projection이라 {@code booking.order.query}가 구현까지 갖는다.
 */
@NullMarked
package com.ticket.booking.order.persistence;

import org.jspecify.annotations.NullMarked;
