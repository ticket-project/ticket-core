/**
 * 주문 생명주기의 use case 조립과 트랜잭션 경계다.
 *
 * <p>주문 생성·조회·취소·만료와 그 조립 helper(선점 이력 기록, 종료 처리, 주문 hold 스냅샷 읽기)가 함께 있다 — 모두 주문 상태를 바꾸는 같은 트랜잭션에서
 * 불리므로 떨어뜨리면 원자성을 어디서 보장하는지 읽히지 않는다. 만료 보정을 주기적으로 깨우는 {@code OrderExpirationTrigger}도 실행 주기만 정하고
 * 곧바로 use case를 부르므로 여기 둔다.
 */
@NullMarked
package com.ticket.booking.order.usecase;

import org.jspecify.annotations.NullMarked;
