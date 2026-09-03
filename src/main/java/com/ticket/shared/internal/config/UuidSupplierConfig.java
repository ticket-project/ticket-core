package com.ticket.shared.internal.config;

import com.ticket.shared.UuidSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * UUID 생성을 주입 지점으로 모은다. 무작위 값을 쓰는 곳은 Redis 어댑터뿐이라 여기에서 제공한다.
 */
@Configuration
public class UuidSupplierConfig {

    @Bean
    public UuidSupplier uuidSupplier() {
        return UUID::randomUUID;
    }
}
