package com.ticket.shared.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.modulith.NamedInterface;

import lombok.Getter;

/**
 * CORS 허용 origin이다. security의 {@code ApiSecurityConfig}(HTTP)와 booking의 {@code WebSocketConfig}(STOMP 핸드셰이크)가 같은 값을 읽어야
 * 해서 shared가 갖는다.
 *
 * <p>{@code @NamedInterface}를 package가 아니라 이 타입에 붙인다 — 같은 package의 전역 설정들은 shared 내부 배선이라 공개하지 않는다.
 */
@NamedInterface("config")
@Getter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

    public void setAllowedOrigins(final List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }
}
