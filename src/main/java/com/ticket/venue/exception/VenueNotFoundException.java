package com.ticket.venue.exception;

import com.ticket.shared.exception.NotFoundException;

/**
 * 요청한 공연장이 없다.
 *
 * <p>{@link NotFoundException}을 상속한다 — 오류 코드 {@code E404}와 HTTP 404 매핑을 그대로 쓴다.
 */
public final class VenueNotFoundException extends NotFoundException {
    public VenueNotFoundException(final long venueId) {
        super("공연장을 찾을 수 없습니다. id=" + venueId);
    }
}
