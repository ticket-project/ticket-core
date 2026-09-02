package com.ticket.identity.internal.application.auth.password;

import com.ticket.identity.internal.domain.member.model.EncodedPassword;
import com.ticket.identity.internal.domain.member.model.RawPassword;

/**
 * 비밀번호를 해싱하고 일치 여부를 검증한다. use case를 실행하기 위한 외부 능력이므로
 * core-app이 소유하고, 실제 해시 알고리즘은 core-infra의 어댑터가 구현한다.
 */
public interface PasswordHasher {

    EncodedPassword hash(RawPassword rawPassword);

    boolean matches(RawPassword rawPassword, EncodedPassword encodedPassword);
}
