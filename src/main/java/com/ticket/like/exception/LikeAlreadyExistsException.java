package com.ticket.like.exception;

import com.ticket.like.LikeType;
import org.springframework.http.HttpStatus;

/**
 * 이미 찜한 대상을 다시 찜하려 했다(동시 요청 race).
 *
 * <p>{@code data}에 memberId·likeType·targetId를 싣는 것은 기존 응답 계약이다({@code error.data}로
 * 나간다). 공개 메시지와 문구가 겹쳐 보이지만 역할이 다르다 — message는 고정 문구, data는 어느
 * 요청이 막혔는지를 좁히는 값이다.
 */
public class LikeAlreadyExistsException extends LikeException {

    private static final String MESSAGE = "이미 찜한 대상입니다.";

    public LikeAlreadyExistsException(final long memberId, final LikeType likeType, final long targetId) {
        super(HttpStatus.CONFLICT, LikeErrorCode.E7001, MESSAGE,
                MESSAGE + " memberId=" + memberId + ", likeType=" + likeType + ", targetId=" + targetId);
    }
}
