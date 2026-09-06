package com.ticket.member.internal.application.auth.oauth2;

/**
 * 소셜 로그인으로 조회하거나 새로 만든 회원의 식별 정보다. 로그인 성공 처리에 필요한 값만 담아
 * 회원 엔티티가 API 계층으로 넘어가지 않게 한다.
 */
public record ProvisionedMember(Long memberId, String role) {
}
