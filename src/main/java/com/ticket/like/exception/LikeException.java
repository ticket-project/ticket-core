package com.ticket.like.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.TicketException;

/** like 업무 오류다. 공개 문구는 {@link LikeErrorCode}가 갖고, HTTP 상태는 handler가 오류 코드로 정한다(LikeExceptionHandler). */
public final class LikeException extends TicketException {
    /** @param data 어느 요청이 막혔는지 좁히는 <b>공개</b> 상세 문구다. 그대로 {@code error.data}로 나가고 고정 문구를 덮지 않는다. */
    public LikeException(final LikeErrorCode errorCode, final @Nullable Object data) {
        super(errorCode, errorCode.getMessage(), data);
    }

    @Override
    public LikeErrorCode getErrorCode() {
        return (LikeErrorCode) super.getErrorCode();
    }
}
