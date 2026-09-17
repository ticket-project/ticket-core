/**
 * show의 DB·외부 시스템 구현.
 *
 * <p>기술 책임으로 한 단계 나눈다. {@code persistence}는 Aggregate 저장 계약의 어댑터와 그 안에서 쓰는 Spring Data 인터페이스를,
 * {@code querydsl}은 조회 port 구현과 그 조건·정렬 조립 helper를 갖는다. 둘은 서로 참조하지 않는다.
 */
@NullMarked
package com.ticket.show.infrastructure;

import org.jspecify.annotations.NullMarked;
