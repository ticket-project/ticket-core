package com.ticket.member.account.application;

import com.ticket.member.account.domain.SocialProvider;

/** DB 탈퇴 처리 전에 보존한 외부 소셜 계정 연결 식별자다. */
public record SocialAccountConnection(SocialProvider provider, String providerId) {
}
