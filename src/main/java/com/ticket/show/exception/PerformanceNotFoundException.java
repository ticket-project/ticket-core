package com.ticket.show.exception;

import com.ticket.shared.exception.NotFoundException;

/**
 * 요청한 회차가 없다.
 *
 * <p>show 업무 예외가 아니라 {@link NotFoundException}을 상속한다 — 오류 코드 {@code E404}와 HTTP 404 매핑을 그대로 쓴다.
 * {@code ShowExceptionHandler}가 잡는 타입이 되면 400으로 응답한다.
 */
public final class PerformanceNotFoundException extends NotFoundException {
    public PerformanceNotFoundException(final Long performanceId) {
        super("회차를 찾을 수 없습니다. id=" + performanceId);
    }
}
