/**
 * like가 다른 module에 공개하는 계약이다.
 *
 * <p>찜 여부·개수·내 찜 목록 조회({@link com.ticket.like.api.LikeQueryApi})와 그 사이를 오가는
 * 값({@link com.ticket.like.api.LikeCountSnapshot}, {@link com.ticket.like.api.LikeSnapshot},
 * {@link com.ticket.like.api.LikeType})이 여기 있다. 구현 ({@code like.usecase}/{@code like.domain}/{@code like.persistence})은
 * 이 module 밖에서 보이지 않는다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.like.api;

import org.jspecify.annotations.NullMarked;
