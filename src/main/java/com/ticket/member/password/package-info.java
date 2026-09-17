/**
 * 비밀번호 해싱 기능이다 — 계약({@code PasswordHasher})과 Spring Security 구현, 그 bean 설정을 한곳에 둔다.
 *
 * <p>해싱은 저장 기술이 아니라 보안 기술이라 {@code persistence}에 두지 않는다. 계약과 구현이 서로 밖에서 쓰이지 않는 작은 기능이라 역할별로 쪼개지 않고
 * 기능 이름 하나로 묶는다.
 */
@NullMarked
package com.ticket.member.password;

import org.jspecify.annotations.NullMarked;
