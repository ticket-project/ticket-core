package com.ticket.identity;

/**
 * 다른 module이 member entity 대신 쓰는 회원 조회·검증 공개 계약이다.
 *
 * <p>존재하지 않거나 탈퇴한 회원은 identity가 소유한
 * {@code com.ticket.core.support.exception.CoreException}({@code ErrorType.NOT_FOUND_DATA})으로
 * 알린다. 어떤 오류로 다룰지는 이 계약이 아니라 전역 오류 계약을 그대로 따른다 — identity가 별도
 * exception 타입을 만들지 않는다.
 */
public interface MemberLookup {

    /**
     * 활성 회원인지 검증한다. 존재하지 않거나 탈퇴한 회원이면 던진다.
     */
    void requireActive(long memberId);

    /**
     * 회원 상태를 조회한다. 존재하지 않으면 던진다.
     */
    MemberStatus getStatus(long memberId);
}
