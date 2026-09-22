package com.ticket.booking.exception;

import com.ticket.shared.exception.NotFoundException;

/**
 * 공연에 회차가 하나도 없다. booking이 showId로 seat-map을 그릴 대표 회차를 찾지 못한 경우다.
 *
 * <p>{@code BookingException}이 아니라 {@link NotFoundException}을 상속한다 — 오류 코드 {@code E404}와 HTTP 404 매핑을 그대로 쓴다.
 */
public final class ShowPerformanceNotFoundException extends NotFoundException {
    public ShowPerformanceNotFoundException(final Long showId) {
        super("공연 회차를 찾을 수 없습니다. showId=" + showId);
    }
}
