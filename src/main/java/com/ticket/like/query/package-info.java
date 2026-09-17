/**
 * like가 요구하는 조회 계약이다.
 *
 * <p>지금은 {@link com.ticket.like.query.LikeQueryPort} 하나뿐이다. 파일 하나를 위한 package는 보통 만들지 않지만, {@code
 * query}는 모든 업무 module에서 같은 자리를 뜻하는 역할 이름이라 예외로 둔다 — 계약과 Querydsl 구현이 한 package에 섞이면 use case가 구현을
 * 직접 부르는 것을 막을 수 없다.
 */
@NullMarked
package com.ticket.like.query;

import org.jspecify.annotations.NullMarked;
