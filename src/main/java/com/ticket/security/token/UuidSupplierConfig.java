package com.ticket.security.token;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * UUID 생성을 주입 지점으로 모은다. 무작위 값을 쓰는 곳은 Redis 어댑터뿐이라 여기에서 제공한다.
 *
 * <p>타입은 {@code java.util.function.Supplier<UUID>}를 그대로 쓴다 — 우리 이름의 interface를 따로 선언해도 계약은 똑같고,
 * 테스트가 발급 값을 고정하는 것도 lambda 하나로 된다.
 */
@Configuration
public class UuidSupplierConfig {
    @Bean
    public Supplier<UUID> uuidSupplier() {
        return UUID::randomUUID;
    }
}
