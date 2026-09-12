/**
 * member가 소유하는 회원·인증·인가 오류 계약이다.
 *
 * <p>전역 HTTP security가 기존 E1000/E1001 응답을 동일하게 만들기 위해 이 계약만 사용한다.
 * handler 구현은 하위 패키지라 공개되지 않는다.
 */
@org.springframework.modulith.NamedInterface("exception")
package com.ticket.member.exception;
