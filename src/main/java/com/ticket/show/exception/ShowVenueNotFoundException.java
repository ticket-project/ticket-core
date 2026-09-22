package com.ticket.show.exception;

import com.ticket.shared.exception.NotFoundException;

/**
 * 공연에 연결된 공연장이 없다. {@code venueId}가 비었거나 venue module에 그 id가 없는 경우를 함께 뜻한다.
 *
 * <p>{@code ShowException}이 아니라 {@link NotFoundException}을 상속한다 — 오류 코드 {@code E404}와 HTTP 404 매핑을 그대로 쓴다.
 */
public final class ShowVenueNotFoundException extends NotFoundException {
    public ShowVenueNotFoundException() {
        super("공연에 연결된 공연장을 찾을 수 없습니다.");
    }
}
