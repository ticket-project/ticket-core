package com.ticket.security.jwt;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    private String issuer = "ticket";
    private @Nullable String secretKey;
    private long accessTokenExpirationSeconds = 1800L;
    private long refreshTokenExpirationSeconds = 86400L;

    /** security.jwt.secret-key는 기본값이 없는 필수 설정이라 바인딩되지 않으면 이 값을 쓰는 bean 생성에서 애플리케이션이 뜨지 않는다. */
    public String getSecretKey() {
        return Objects.requireNonNull(secretKey, "security.jwt.secret-key must be configured");
    }
}
