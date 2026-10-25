package com.ticket.security.token;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * security의 Redis 어댑터(refresh token·OAuth2 인증 코드 저장소)가 쓰는 UUID 생성을 주입 지점으로 둔다. 다른 module은 이 bean을 쓰지 않고
 * {@code UUID.randomUUID()}를 직접 부른다.
 *
 * <p>타입은 {@code java.util.function.Supplier<UUID>}를 그대로 쓴다 — 우리 이름의 interface를 따로 선언해도 계약은 똑같고, 테스트가 발급 값을 고정하는 것도
 * lambda 하나로 된다.
 */
@Configuration
public class UuidSupplierConfig {
    @Bean
    public Supplier<UUID> uuidSupplier() {
        return UUID::randomUUID;
    }
}
