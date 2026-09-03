package com.ticket.booking.internal.application.support;

import com.ticket.booking.internal.exception.ExceedHoldLimitException;
import com.ticket.booking.internal.exception.NotYetReserveTimeException;
import com.ticket.booking.internal.exception.PerformanceIsPastException;
import com.ticket.catalog.BookingPolicySnapshot;

import java.time.LocalDateTime;

/**
 * catalog의 {@link BookingPolicySnapshot}으로 booking이 스스로 판정해야 하는 예매 가능 여부를
 * 확인한다. catalog internal {@code BookingPolicyValidator}는 module 경계 밖이라 쓸 수 없고,
 * {@link BookingPolicySnapshot#bookingOpen()}은 오픈 전/마감 후 두 사유를 구분하지 않으므로
 * 판정 자체는 공개된 {@code orderOpenTime}/{@code orderCloseTime} 필드로 다시 계산한다.
 */
public final class BookingPolicyGuard {

    private BookingPolicyGuard() {
    }

    /**
     * 예매 가능 시각 안인지 확인한다. 판정 시점의 시각(now)으로 비교한다.
     */
    public static void ensureBookingOpen(final BookingPolicySnapshot policy, final LocalDateTime now) {
        if (policy.orderOpenTime() == null || now.isBefore(policy.orderOpenTime())) {
            throw new NotYetReserveTimeException();
        }
        if (policy.orderCloseTime() == null || now.isAfter(policy.orderCloseTime())) {
            throw new PerformanceIsPastException();
        }
    }

    /**
     * 한도가 없는 회차는 좌석 수를 제한하지 않는다.
     */
    public static void ensureWithinHoldLimit(final BookingPolicySnapshot policy, final long requestedSeatCount) {
        if (policy.maxCanHoldCount() == null) {
            return;
        }
        if (requestedSeatCount > policy.maxCanHoldCount()) {
            throw new ExceedHoldLimitException();
        }
    }
}
