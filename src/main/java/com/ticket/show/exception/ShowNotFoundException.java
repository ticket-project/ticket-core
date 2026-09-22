package com.ticket.show.exception;

import com.ticket.shared.exception.NotFoundException;

/**
 * 요청한 공연이 없다.
 *
 * <p>{@code ShowException}이 아니라 {@link NotFoundException}을 상속한다 — 오류 코드 {@code E404}와 HTTP 404 매핑을 그대로 쓴다.
 */
public final class ShowNotFoundException extends NotFoundException {
    public ShowNotFoundException(final Long showId) {
        super("공연을 찾을 수 없습니다. id=" + showId);
    }
}
