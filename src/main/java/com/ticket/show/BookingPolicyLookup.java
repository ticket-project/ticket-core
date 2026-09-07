package com.ticket.show;

/**
 * booking이 예매 가능 여부·hold 한도·대기열 필요 여부를 조회하는 공개 계약이다.
 *
 * <p>존재하지 않는 회차 ID는 공통 오류
 * ({@code com.ticket.error.NotFoundException})으로 알린다. 어떤 오류로 다룰지는 이 계약이 아니라 공통 오류
 * 계약을 그대로 따른다 — show가 별도 exception 타입을 만들지 않는다.
 *
 * <p>좌석 가격은 이 계약이 다루지 않는다. 판매 좌석의 가격 원본은 booking이 소유한
 * {@code PerformanceSeat.unitPrice}이고, 좌석 표시값(등급·좌석 라벨)이 필요하면
 * {@link PerformanceSaleCatalog}을 쓴다(ADR 0005).
 */
public interface BookingPolicyLookup {

    BookingPolicySnapshot getBookingPolicy(long performanceId);
}
