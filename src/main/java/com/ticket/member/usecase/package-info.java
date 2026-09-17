/**
 * member의 회원 workflow와 트랜잭션 경계다.
 *
 * <p>공개 계약({@code member.api})의 구현과 요청 단위 use case가 함께 있다. 회원 계정 처리를 읽으려면 여기 하나만 보면 된다 — 계정 다섯 연산,
 * 소셜 신원 해석, 현재 회원 조회가 전부 같은 업무의 다른 진입점이라 package를 나누지 않는다.
 */
@NullMarked
package com.ticket.member.usecase;

import org.jspecify.annotations.NullMarked;
