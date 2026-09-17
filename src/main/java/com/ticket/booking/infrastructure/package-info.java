/**
 * booking의 DB·Redis·외부 시스템 구현.
 *
 * <p>파일 수가 많아 기술 책임으로 한 단계 나눈다. {@code persistence}는 Aggregate 저장 어댑터와 Spring Data 인터페이스, {@code
 * querydsl}은 조회 port의 Querydsl 구현, {@code redis}는 hold·좌석 선점 저장과 분산락·키 만료 처리, {@code websocket}은
 * STOMP 배선과 좌석 상태 발행, {@code admission}은 입장 토큰 발급 설정과 검증이다.
 *
 * <p>root에 남은 {@link com.ticket.booking.order.usecase.OrderExpirationTrigger}는 어느 기술 묶음에도 속하지 않는
 * 스케줄 배선 하나뿐이라 그것만을 위한 package를 만들지 않았다.
 */
@NullMarked
package com.ticket.booking.infrastructure;

import org.jspecify.annotations.NullMarked;
