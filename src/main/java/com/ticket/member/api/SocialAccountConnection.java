package com.ticket.member.api;

/**
 * DB 탈퇴 처리 전에 보존한 외부 소셜 계정 연결 식별자다. 탈퇴를 조립하는 쪽이 외부 provider 연결 해제에 쓰라고 member가 돌려주는 공개 값이라 module
 * root에 둔다 — entity나 저장소를 넘기지 않는다.
 */
public record SocialAccountConnection(SocialProvider provider, String providerId) {}
