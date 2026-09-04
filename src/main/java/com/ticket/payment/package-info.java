/**
 * Payment module: Order에 대한 결제 시도(Payment)의 생명주기를 소유한다.
 *
 * <p>ADR 0005에 따라 이번 구현은 entity/schema/repository와 구조·중복 방지 테스트까지만 다룬다.
 * PG client, 결제 승인/실패/취소 API, callback/webhook, {@code OrderConfirmed} listener는 이번
 * 범위가 아니다.
 *
 * <p>booking의 Order를 참조할 때 {@code orderId}는 scalar {@code Long} 컬럼일 뿐 JPA 연관관계가
 * 아니다 — cross-module JPA 관계와 물리 FK는 금지된다(ADR 0003, ADR 0005 §4).
 *
 * <p>이번 entity-only 단계에서 payment는 다른 업무 모듈을 import하지 않는 leaf module이다. 실제
 * PG 정산을 구현하는 후속 단계에서만 {@code payment -> booking} 공개 계약 의존이 추가된다(ADR 0005
 * §4).
 */
@ApplicationModule(displayName = "Payment", allowedDependencies = {})
package com.ticket.payment;

import org.springframework.modulith.ApplicationModule;
