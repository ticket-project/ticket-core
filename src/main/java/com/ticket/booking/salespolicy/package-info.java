/**
 * 판매 정책 capability다 — 회차의 예매 접수 기간, Hold 한도, 대기열 진입 정책과 그 판정을 소유한다.
 *
 * <p>거의 전부가 domain 타입이지만 정책 조회 use case와 JPA 구현, 인증 없이 열려 있는 예매 방식 조회 endpoint가 함께 있어 역할별로 나눈다.
 *
 * <p><b>Spring Modulith Application Module이 아니다.</b> booking 안에서 업무 단위로 탐색하기 위한 ordinary package다.
 */
@NullMarked
package com.ticket.booking.salespolicy;

import org.jspecify.annotations.NullMarked;
