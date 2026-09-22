/**
 * 주문 capability다 — Order/OrderSeat aggregate와 그 생명주기(생성·조회·취소·만료)를 소유한다.
 *
 * <p><b>Spring Modulith Application Module이 아니다.</b> booking module 안에서 업무 단위로 탐색하기 위한 ordinary package일 뿐이라
 * {@code @ApplicationModule}도 {@code @NamedInterface}도 붙이지 않는다. module 경계와 의존 DAG는 {@code com.ticket.booking} 하나가 그대로
 * 갖는다.
 */
@NullMarked
package com.ticket.booking.order;

import org.jspecify.annotations.NullMarked;
