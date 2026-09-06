package com.ticket.booking.application.lock;

/**
 * 락이 보호하는 대상의 업무 의미다.
 *
 * <p>실제 Redis key 형식은 core-infra가 정한다. app은 무엇을 잠그는지만 말한다.
 */
public enum LockScope {

    /** 회차의 좌석 한 자리. 동시 점유를 막는다. */
    SEAT,

    /** 같은 회원과 회차의 주문 시작. 중복 주문 시작을 막는다. */
    ORDER_START
}
