/**
 * Booking BC. {@code OrderCreated}/{@code OrderTerminated}는 {@code booking.api}가 아니라 module root에 둔다 — FQCN이 Modulith
 * event publication registry({@code EVENT_PUBLICATION.event_type})에 저장돼 있어 옮기면 미완료 publication이 재처리되지 않는다. listener id도
 * 같은 이유로 옛 package 문자열을 유지하고 {@code BookingEventListenerIdContractTest}가 이를 고정한다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Booking",
        allowedDependencies = {
            "show :: api",
            "member :: api",
            "security :: api",
            "shared :: api",
            "shared :: config",
            "shared :: web",
            "shared :: exception",
            "shared :: jpa"
        })
package com.ticket.booking;

import org.jspecify.annotations.NullMarked;
