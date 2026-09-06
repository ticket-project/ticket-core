package com.ticket.member;

/**
 * 다른 module이 회원 표시값(이름·이메일)이 필요할 때 쓰는 공개 계약이다. JPA {@code Member} entity를
 * 노출하지 않는다. {@link MemberLookup#getProfile(long)}은 탈퇴한 회원을 존재하지 않는 회원과 같이
 * 다루므로, 이 record가 성공적으로 반환됐다면 활성 회원이다.
 */
public record MemberProfile(long memberId, String name, String email) {
}
