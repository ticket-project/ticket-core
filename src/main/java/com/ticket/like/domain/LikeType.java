package com.ticket.like.domain;

/**
 * 찜 대상의 종류다. 지금은 공연(Show) 하나뿐이라 {@code SHOW}만 있고, {@link Like}는 항상 {@code SHOW}로 저장한다(ADR 0008의 2026-10-07 갱신).
 *
 * <p>{@code like_type} 컬럼과 유니크 제약은 그대로 두었다. 공연장·출연자 찜처럼 새 대상이 실제로 생기면 값을 추가하고 Repository 조회에 종류 조건을 다시 넣는다.
 */
public enum LikeType {
    SHOW
}
