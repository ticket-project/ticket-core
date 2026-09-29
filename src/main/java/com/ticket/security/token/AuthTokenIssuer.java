package com.ticket.security.token;

/** 액세스·리프레시 토큰을 발급한다. 토큰 발급에 필요한 것은 회원 식별자와 권한뿐이므로 엔티티가 아니라 값을 받는다. */
public interface AuthTokenIssuer {
    IssuedAuthTokens issueTokens(Long memberId, String role);
}
