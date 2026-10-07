package com.ticket.member.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.TicketException;

/** member 업무 오류다. 공개 문구는 {@link MemberErrorCode}가 갖고, HTTP 상태는 handler가 오류 코드로 정한다(MemberExceptionHandler). */
public final class MemberException extends TicketException {
    /** @param data 어느 경로에서 실패했는지 좁히는 <b>공개</b> 상세 문구다. 그대로 {@code error.data}로 나가고 고정 문구를 덮지 않는다. */
    public MemberException(final MemberErrorCode errorCode, final @Nullable Object data) {
        super(errorCode, errorCode.getMessage(), data);
    }

    @Override
    public MemberErrorCode getErrorCode() {
        return (MemberErrorCode) super.getErrorCode();
    }
}
