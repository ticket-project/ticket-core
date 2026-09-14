package com.ticket.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    private String issuer = "ticket";
    private String secretKey;
    private long accessTokenExpirationSeconds = 1800L;
    private long refreshTokenExpirationSeconds = 86400L;
}
