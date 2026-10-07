package com.ticket.booking.admission;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * admission token 검증 설정이다. 빈 값이면 기동이 실패하고, secret이 HS256에 필요한 32바이트보다 짧으면 {@code JwtAdmissionVerifier} 생성이 실패해 기동을 막는다.
 *
 * <p>만료 시간은 담지 않는다 — Core는 토큰 자체의 {@code exp} claim으로 만료를 판정하고({@code JwtAdmissionVerifier}가 주입된 clock으로 판정한다), 발급 시점의
 * TTL은 토큰을 만드는 {@code ticket-queue}가 정한다. 그래서 Core 설정에는 만료 시간 키가 없다.
 */
@Validated
@ConfigurationProperties(prefix = "security.admission")
public record AdmissionTokenProperties(
        boolean enforcementEnabled,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String secretKey) {}
