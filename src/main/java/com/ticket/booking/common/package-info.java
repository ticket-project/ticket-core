/**
 * Booking module 안의 여러 업무(order, hold, selection, seat, salespolicy, admission, ticket)가 함께 쓰는 공통
 * 기반이다.
 *
 * <p><b>하위 package를 만들지 않는다.</b> {@code common.application}/{@code common.domain}/{@code
 * common.infrastructure}나 {@code common.lock}/{@code common.redis}로 다시 나누지 않는다 — 클래스 열한 개를 다시 계층으로
 * 쪼개면 "업무 코드인가 기반인가"라는 이 package의 유일한 구분이 흐려진다. 역할별 의존 방향은 package 경계가 아니라 {@code
 * BookingCommonDependencyTest}가 강제한다.
 *
 * <p>이곳은 별도 Application Module도, 외부에 공개하는 NamedInterface도 아니다. booking 밖에서 쓰지 않는다. 반대로 예매에 특화된
 * 기반이어도 되고, 전역 {@code shared}로 일반화하지 않는다.
 *
 * <p>배치 기준: booking 안의 여러 업무가 쓰고 특정 업무 하나의 소유라고 보기 어려운 기반만 둔다. 여러 곳에서 호출된다는 사실만으로는 옮기지 않는다 — 주문
 * 생성처럼 여러 업무를 조립하는 코드는 결과를 책임지는 {@code order.application}에 둔다. 예외는 {@code booking.exception}에 그대로
 * 둔다.
 */
package com.ticket.booking.common;
