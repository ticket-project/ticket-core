package com.ticket.member.api;

import org.jspecify.annotations.Nullable;

/**
 * 외부 provider별 응답 형식을 제거한 정규화된 소셜 신원이다.
 *
 * <p>provider 응답을 이 형태로 해석하는 일은 security.oauth가 하고, 이 값으로 계정을 찾거나 만드는 일은 member가 한다. 그 경계에 놓이는 값이라
 * member root의 공개 계약이다.
 *
 * @param email provider가 이메일 제공에 동의받지 못하면 응답에서 빠지므로 null일 수 있다
 * @param emailVerified provider가 이메일을 검증했는지. <b>검증된 이메일만</b> 기존 계정 연결에 쓴다
 * @param name provider가 닉네임/이름을 주지 않으면 null일 수 있다
 */
public record SocialIdentity(
        SocialProvider provider,
        String providerId,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String name) {}
