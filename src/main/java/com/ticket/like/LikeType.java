package com.ticket.like;

/**
 * 찜 대상의 종류다. 지금은 공연(Show) 하나뿐이라 {@code SHOW}만 있다.
 *
 * <p>공연장·출연자 찜처럼 새 대상이 생기면 여기에 값을 추가한다 — 새 module이나 새 테이블을
 * 만들지 않는다. 다만 대상별 표시값 조립과 대상 존재 확인은 여전히 그 대상을 아는 module의
 * 책임으로 남는다(이 열거형이 줄여주는 것은 테이블·리포지토리·중복 방지 불변식뿐이다).
 */
public enum LikeType {
    SHOW
}
