package com.ticket.member;

/**
 * 다른 module이 회원 상태를 확인할 때 쓰는 공개 값이다. JPA {@code Member} entity를 노출하지 않고,
 * 인가·활성 여부 판단에 필요한 최소 필드만 담는다. 이메일·이름 같은 개인정보는 이 계약 밖이다 —
 * 그 정보가 필요한 module은 아직 없고, 필요해지면 그때 별도 계약으로 추가한다.
 */
public record MemberStatus(Long memberId, boolean active, String role) {
}
