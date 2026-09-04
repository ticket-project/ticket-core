/**
 * Ticketing module: 결제 성공으로 확정된 OrderSeat에 대해 발급되는 Ticket(입장 권리)의 생명주기를
 * 소유한다.
 *
 * <p>ADR 0005에 따라 이번 구현은 entity/schema/repository와 구조·중복 방지 테스트까지만 다룬다.
 * {@code OrderConfirmed} listener, 자동 티켓 발급, QR, 입장, 사용, 취소, 환불, 양도 API는 이번
 * 범위가 아니다.
 *
 * <p>booking의 OrderSeat, identity의 Member를 참조할 때 {@code orderSeatId}/{@code ownerMemberId}는
 * scalar {@code Long} 컬럼일 뿐 JPA 연관관계가 아니다 — cross-module JPA 관계와 물리 FK는
 * 금지된다(ADR 0003, ADR 0005 §4).
 *
 * <p>이번 entity-only 단계에서 ticketing은 다른 업무 모듈을 import하지 않는 leaf module이다. 실제
 * {@code OrderConfirmed} 구독을 구현하는 후속 단계에서만 {@code ticketing -> booking} 공개 계약
 * 의존이 추가된다(ADR 0005 §4).
 */
@ApplicationModule(displayName = "Ticketing", allowedDependencies = {})
package com.ticket.ticketing;

import org.springframework.modulith.ApplicationModule;
