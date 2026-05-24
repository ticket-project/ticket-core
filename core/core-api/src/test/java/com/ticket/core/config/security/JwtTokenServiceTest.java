package com.ticket.core.config.security;

import com.ticket.core.domain.member.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void createAndParseAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("ticket");
        properties.setSecretKey("12345678901234567890123456789012");
        properties.setAccessTokenExpirationSeconds(1800L);
        JwtTokenService jwtTokenService = new JwtTokenService(properties);

        String token = jwtTokenService.createAccessToken(new MemberPrincipal(7L, Role.MEMBER));

        MemberPrincipal principal = jwtTokenService.parse(token);
        assertThat(principal.getMemberId()).isEqualTo(7L);
        assertThat(principal.getRole()).isEqualTo(Role.MEMBER);
        assertThat(jwtTokenService.getAccessTokenExpirationSeconds()).isEqualTo(1800L);
    }
}
