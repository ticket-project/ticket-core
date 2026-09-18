/**
 * Order aggregate의 저장 adapter와 local DB 조회 Repository 구현이다.
 *
 * <p>저장/복원 계약({@code booking.order.domain.OrderRepository})은 밖에 있다 — 여기에는 그 계약을 만족시키는 저장 기술과, 그와 같은
 * 기술을 쓰는 읽기 모델 projection 조회를 둔다. 읽기 모델 타입 자체는 {@code booking.order.query}에 있다.
 */
@NullMarked
package com.ticket.booking.order.persistence;

import org.jspecify.annotations.NullMarked;
