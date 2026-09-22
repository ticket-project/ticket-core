/**
 * 이벤트 후속 처리의 중간 진행 상태를 남기는 JPA 구현이다.
 *
 * <p>Redis 해제까지 끝났음을 발행 전에 기록해 같은 event가 재전달돼도 해제를 반복하지 않는다. 계약({@code HoldReleaseProgressRecorder})은
 * {@code booking.event}에 있다.
 */
@NullMarked
package com.ticket.booking.event.persistence;

import org.jspecify.annotations.NullMarked;
