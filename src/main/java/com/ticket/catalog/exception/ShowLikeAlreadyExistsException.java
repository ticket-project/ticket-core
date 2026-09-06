package com.ticket.catalog.exception;

import org.springframework.http.HttpStatus;

/**
 * 이미 찜한 공연을 다시 찜하려 했다.
 *
 * <p>{@code data}에 memberId·showId를 싣는 것은 기존 응답 계약이다({@code error.data}로 나간다).
 * 공개 메시지와 문구가 겹쳐 보이지만 역할이 다르다 — message는 고정 문구, data는 어느 요청이
 * 막혔는지를 좁히는 값이다.
 */
public class ShowLikeAlreadyExistsException extends CatalogException {

    private static final String MESSAGE = "이미 찜한 공연입니다.";

    public ShowLikeAlreadyExistsException(final Long memberId, final Long showId) {
        super(HttpStatus.CONFLICT, CatalogErrorCode.E7001, MESSAGE,
                MESSAGE + " memberId=" + memberId + ", showId=" + showId);
    }
}
