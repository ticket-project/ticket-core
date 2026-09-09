package com.ticket.member.application;

import com.ticket.member.AuthenticatedMember;

/**
 * 액세스 토큰을 읽은 결과다.
 *
 * <p>토큰 기술의 예외 타입이 이 경계를 넘지 않도록 중립 값으로 표현한다.
 * 만료와 그 밖의 실패를 구분하는 이유는, API가 응답을 다르게 안내할 수 있기 때문이다.
 */
public sealed interface AccessTokenReadResult {

    record Authenticated(AuthenticatedMember member) implements AccessTokenReadResult {
    }

    record Expired() implements AccessTokenReadResult {
    }

    record Invalid() implements AccessTokenReadResult {
    }

    static AccessTokenReadResult authenticated(final AuthenticatedMember member) {
        return new Authenticated(member);
    }

    static AccessTokenReadResult expired() {
        return new Expired();
    }

    static AccessTokenReadResult invalid() {
        return new Invalid();
    }
}
