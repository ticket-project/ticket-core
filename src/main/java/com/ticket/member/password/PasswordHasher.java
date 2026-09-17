package com.ticket.member.password;

import com.ticket.member.api.RawPassword;
import com.ticket.member.domain.EncodedPassword;

/**
 * 비밀번호를 해싱하고 일치 여부를 검증한다. 계약은 member가 소유하고, 실제 해시 알고리즘은 같은 package의 {@code
 * SpringSecurityPasswordHasher}가 구현한다.
 */
public interface PasswordHasher {
    EncodedPassword hash(RawPassword rawPassword);

    boolean matches(RawPassword rawPassword, EncodedPassword encodedPassword);
}
