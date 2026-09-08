package com.ticket.member.application.auth.password;

import com.ticket.member.domain.member.model.EncodedPassword;
import com.ticket.member.domain.member.model.RawPassword;

/**
 * 비밀번호를 해싱하고 일치 여부를 검증한다. use case를 실행하기 위한 외부 능력이므로
 * 이 인터페이스가 속한 {@code member.application.auth.password}가 소유하고, 실제 해시 알고리즘은
 * {@code member.infrastructure.auth.password}의 {@code SpringSecurityPasswordHasher}가 구현한다.
 */
public interface PasswordHasher {

    EncodedPassword hash(RawPassword rawPassword);

    boolean matches(RawPassword rawPassword, EncodedPassword encodedPassword);
}
