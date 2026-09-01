package com.ticket.core.app.lock;

/**
 * 락이 보호하는 대상의 업무 의미다.
 *
 * <p>실제 Redis key 형식은 core-infra가 정한다. app은 무엇을 잠그는지만 말한다.
 */
public enum LockScope {

    /** 회차의 좌석 한 자리. 동시 점유를 막는다. */
    SEAT,

    /** 같은 회원과 회차의 주문 시작. 중복 주문 시작을 막는다. */
    ORDER_START,

    /** hold 생성 후처리 outbox 한 건. 같은 건의 중복 실행을 막는다. */
    HOLD_CREATION_OUTBOX_ENTRY,

    /** hold 해제 후처리 outbox 한 건. */
    HOLD_RELEASE_OUTBOX_ENTRY,

    /** hold 생성 후처리 보정 배치. 인스턴스가 여럿이어도 한 번만 돌게 한다. */
    HOLD_CREATION_OUTBOX_BATCH,

    /** hold 해제 후처리 보정 배치. */
    HOLD_RELEASE_OUTBOX_BATCH
}
