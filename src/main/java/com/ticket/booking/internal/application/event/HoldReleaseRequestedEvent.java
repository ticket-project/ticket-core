package com.ticket.booking.internal.application.event;

/**
 * hold 해제 후처리가 필요하다는 커밋 후 트리거다.
 */
public record HoldReleaseRequestedEvent(Long eventId) {
}
