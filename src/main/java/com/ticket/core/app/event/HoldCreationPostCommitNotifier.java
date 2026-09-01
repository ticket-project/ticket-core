package com.ticket.core.app.event;

/**
 * 커밋 직후 hold 생성 후처리를 실행하도록 요청한다.
 *
 * <p>실행 자체를 보장하지는 않는다. 제출이 실패해도 기록된 이벤트를 보정 트리거가 다시 처리한다.
 */
public interface HoldCreationPostCommitNotifier {

    void notify(Long eventId);
}
