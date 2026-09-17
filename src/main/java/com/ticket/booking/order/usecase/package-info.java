/**
 * 주문 생명주기의 use case 조립과 트랜잭션 경계다.
 *
 * <p>예매 시작과 주문 생성·조회·취소·만료, 그 조립 helper(선점 이력 기록, 종료 처리, 주문 hold 스냅샷 읽기)가 함께 있다 — 모두 주문 상태를 바꾸는 같은
 * 트랜잭션에서 불리므로 떨어뜨리면 원자성을 어디서 보장하는지 읽히지 않는다. 만료 보정을 주기적으로 깨우는 {@code OrderExpirationTrigger}도 실행 주기만
 * 정하고 곧바로 use case를 부르므로 여기 둔다.
 *
 * <p>{@code StartBookingUseCase}는 판매 정책·대기열 입장·회원·좌석 가용성·선점·보상을 함께 조율하지만, 그 workflow가 책임지는 결과는
 * 주문이다({@code OrderStarted}를 발행한다). 그래서 module 바로 아래가 아니라 order가 소유한다 — 주문 없이 선점만 만드는 흐름이 생기면 그때 다시
 * 본다.
 */
@NullMarked
package com.ticket.booking.order.usecase;

import org.jspecify.annotations.NullMarked;
