package com.ticket.core.config.security;

import com.ticket.core.domain.member.model.Role;
import com.ticket.support.security.jwt.JwtAccessTokenIssuer;
import com.ticket.support.security.jwt.JwtMemberClaims;
import com.ticket.support.security.jwt.JwtTokenVerifier;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final JwtAccessTokenIssuer jwtAccessTokenIssuer;
    private final JwtTokenVerifier jwtTokenVerifier;

    public JwtTokenService(final JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        final com.ticket.support.security.jwt.JwtProperties sharedProperties =
                new com.ticket.support.security.jwt.JwtProperties(
                        jwtProperties.getIssuer(),
                        jwtProperties.getSecretKey(),
                        jwtProperties.getAccessTokenExpirationSeconds()
                );
        this.jwtAccessTokenIssuer = new JwtAccessTokenIssuer(sharedProperties);
        this.jwtTokenVerifier = new JwtTokenVerifier(sharedProperties);
    }

    public String createAccessToken(final MemberPrincipal memberPrincipal) {
        return jwtAccessTokenIssuer.issue(memberPrincipal.getMemberId(), memberPrincipal.getRole().name());
    }

    public MemberPrincipal parse(final String token) {
        final JwtMemberClaims claims = jwtTokenVerifier.verify(token);
        return new MemberPrincipal(claims.memberId(), Role.valueOf(claims.role()));
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpirationSeconds();
    }
}
