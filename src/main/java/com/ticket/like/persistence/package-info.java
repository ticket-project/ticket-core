/**
 * like의 JPA 저장 adapter와 Querydsl 조회({@code LikeQuerydslRepository}) 구현이다. 저장 계약({@code like.domain.LikeRepository})은
 * domain이, 조회 결과 타입({@code like.api})은 공개면이 갖는다. 조회는 엔티티를 돌려주고 공개 계약으로의 변환은 {@code like.usecase}가 한다.
 */
@NullMarked
package com.ticket.like.persistence;

import org.jspecify.annotations.NullMarked;
