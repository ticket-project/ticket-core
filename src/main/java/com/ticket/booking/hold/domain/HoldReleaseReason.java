package com.ticket.booking.hold.domain;

public enum HoldReleaseReason {
    PAYMENT_CONFIRMED,
    TTL_EXPIRED,
    USER_CANCELED,
    ORDER_EXPIRED,
    PAYMENT_FAILED
}
