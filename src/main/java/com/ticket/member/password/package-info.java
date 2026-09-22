/**
 * 비밀번호 해싱 bean 설정이다. 해싱과 일치 확인 자체는 Spring Security의 {@code PasswordEncoder}가 한다 — member는 그 bean을 소유하고
 * {@code MemberAccountService}가 직접 쓴다.
 *
 * <p>해싱은 저장 기술이 아니라 보안 기술이라 {@code persistence}에 두지 않는다. member가 이 bean을 소유하므로 {@code member -> security} 의존이 생기지 않는다.
 */
@NullMarked
package com.ticket.member.password;

import org.jspecify.annotations.NullMarked;
