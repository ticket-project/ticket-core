package com.ticket.member.oauth.domain;

import com.ticket.member.account.domain.SocialProvider;

/** 외부 provider별 응답 형식을 제거한 정규화된 소셜 신원이다. */
public record OAuth2UserInfo(
        SocialProvider provider,
        String providerId,
        String email,
        boolean emailVerified,
        String name
) {
}
