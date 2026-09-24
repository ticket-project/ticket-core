package com.ticket.member.exception;

import com.ticket.shared.exception.NotFoundException;

/**
 * 요청한 회원이 없거나 탈퇴했다.
 *
 * <p>{@link NotFoundException}을 상속해 오류 코드 {@code E404}와 HTTP 404 매핑을 그대로 쓴다.
 *
 * <p>생성자가 둘인 이유는 {@code error.data}를 흘리지 않는 자리가 있어서다. 인증된 본인을 조회하는 경로는 id를 담지 않는다 — 어차피 호출자가 아는 값이고, 담지 않던 응답에 값을 새로 넣지
 * 않는다.
 */
public final class MemberNotFoundException extends NotFoundException {
    public MemberNotFoundException() {
        super();
    }

    public MemberNotFoundException(final long memberId) {
        super("회원을 찾을 수 없습니다. id=" + memberId);
    }
}
