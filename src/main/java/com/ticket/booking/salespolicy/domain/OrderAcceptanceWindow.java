package com.ticket.booking.salespolicy.domain;

import com.ticket.shared.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 새로운 주문·좌석 선택을 시작할 수 있는 접수 기간이다. Hold/Order의 {@code expiresAt}(이미 시작된
 * 개별 주문의 완료 기한)과는 다른 개념이다 — 접수 종료 직전에 생성된 Order/Hold의 {@code expiresAt}을
 * 이 window의 {@code closesAt}으로 잘라내지 않는다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderAcceptanceWindow {

    @Column(name = "order_opens_at", nullable = false)
    private LocalDateTime opensAt;

    @Column(name = "order_closes_at", nullable = false)
    private LocalDateTime closesAt;

    public OrderAcceptanceWindow(final LocalDateTime opensAt, final LocalDateTime closesAt) {
        if (opensAt == null) {
            throw new InvalidRequestException("opensAt는 필수입니다.");
        }
        if (closesAt == null) {
            throw new InvalidRequestException("closesAt는 필수입니다.");
        }
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidRequestException("opensAt는 closesAt보다 이전이어야 합니다.");
        }
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    /**
     * 마감 시각과 정확히 같은 순간은 접수 가능(OPEN)하다.
     */
    public OrderAcceptanceStatus statusAt(final LocalDateTime now) {
        if (now.isBefore(opensAt)) {
            return OrderAcceptanceStatus.BEFORE_OPEN;
        }
        if (now.isAfter(closesAt)) {
            return OrderAcceptanceStatus.CLOSED;
        }
        return OrderAcceptanceStatus.OPEN;
    }
}
