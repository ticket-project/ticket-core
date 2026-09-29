package com.ticket.member.api;

/**
 * 다른 module이 회원 신원(memberId, role)을 확인할 때 쓰는 공개 값이다. JPA {@code Member} entity를 노출하지 않고, 인가에 필요한 최소 필드만 담는다. 이메일·이름 같은
 * 개인정보는 이 계약 밖이다 — 그 정보가 필요한 module은 아직 없고, 필요해지면 그때 별도 계약으로 추가한다.
 */
public record MemberIdentity(Long memberId, String role) {}
