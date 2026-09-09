/**
 * Payment BC: Order에 대한 결제 시도(Payment)의 생명주기를 소유한다.
 *
 * payment는 entity-only 단계다 — PG 연동·승인/취소 API·controller는 없다(ADR 0005).
 *
 * <p>booking의 Order를 참조할 때 {@code orderId}는 scalar {@code Long} 컬럼일 뿐 JPA 연관관계가
 * 아니다 — cross-module JPA 관계와 물리 FK는 금지된다(ADR 0003).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payment", allowedDependencies = {})
package com.ticket.payment;

