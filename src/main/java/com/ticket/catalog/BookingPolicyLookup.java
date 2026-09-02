package com.ticket.catalog;

import java.util.List;

/**
 * booking이 예매 가능 여부·hold 한도·대기열 필요 여부·좌석 가격을 한 번에 조회하는 공개 계약이다.
 *
 * <p>존재하지 않는 회차 ID는 catalog가 소유한 {@code com.ticket.core.support.exception.CoreException}
 * ({@code ErrorType.NOT_FOUND_DATA})으로 알린다. 어떤 오류로 다룰지는 이 계약이 아니라 전역 오류
 * 계약을 그대로 따른다 — catalog가 별도 exception 타입을 만들지 않는다.
 */
public interface BookingPolicyLookup {

    /**
     * @param seatIds 가격을 함께 조회할 좌석 ID. 비어 있으면 {@link BookingPolicySnapshot#seatPrices()}도 빈 맵이다.
     */
    BookingPolicySnapshot getBookingPolicy(long performanceId, List<Long> seatIds);
}
