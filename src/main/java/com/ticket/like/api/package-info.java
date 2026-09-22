/**
 * like가 다른 module에 공개하는 계약이다.
 *
 * <p>찜 개수·내 찜 목록 조회({@link com.ticket.like.api.LikeQueryApi})와 그것이 돌려주는 값({@link com.ticket.like.api.LikeSnapshot})이 여기
 * 있다. 구현({@code like.usecase}/{@code like.domain}/{@code like.persistence})은 이 module 밖에서 보이지 않는다.
 *
 * <p>찜 대상 종류({@code LikeType})는 LIKES 테이블에 저장되는 like 내부 값이라 {@code like.domain}에 있다. 공개면은 대상별 메서드로 나뉘어 종류를 값으로 받지 않는다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.like.api;

import org.jspecify.annotations.NullMarked;
