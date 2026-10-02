package com.ticket.booking.salespolicy.domain;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.ticket.booking.exception.BookingNotOpenYetException;
import com.ticket.booking.exception.HoldLimitExceededException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.shared.jpa.AuditedEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회차 하나의 예매 기간·Hold 제한·대기열 정책을 소유하는 Booking BC aggregate다(ADR 0006 "Performance의 책임 혼재" A2). {@code performanceId}는
 * show가 소유한 {@code Performance}에 대한 scalar 식별자일 뿐 cross-module JPA 연관관계나 DB FK가 아니다.
 *
 * <p>정책 조회 실패("이 회차는 Booking 판매 정책이 구성되지 않음")는 이 aggregate가 아니라 그것을 조회하는 use case가
 * {@link com.ticket.shared.exception.NotFoundException}으로 판단한다 — 이 도메인은 존재 여부를 스스로 판단하지
 * 않는다({@link com.ticket.shared.exception.NotFoundException} 계약 참고).
 */
@Getter
@Entity
@Table(name = "BOOKING_PERFORMANCE_SALES_POLICIES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceSalesPolicy extends AuditedEntity {
    @Id
    @Column(name = "performance_id")
    private Long performanceId;

    @Embedded
    private BookingWindow bookingWindow;

    @Embedded
    private HoldPolicy holdPolicy;

    @Embedded
    private QueuePolicy queuePolicy;

    @Version
    private Long version;

    public PerformanceSalesPolicy(
            final Long performanceId,
            final BookingWindow bookingWindow,
            final HoldPolicy holdPolicy,
            final QueuePolicy queuePolicy) {
        this.performanceId = performanceId;
        this.bookingWindow = bookingWindow;
        this.holdPolicy = holdPolicy;
        this.queuePolicy = queuePolicy;
    }

    public BookingWindowStatus bookingWindowStatus(final LocalDateTime now) {
        return bookingWindow.statusAt(now);
    }

    /** 예매 기간 안인지 확인한다. 옛 {@code BookingPolicyGuard.ensureBookingOpen}과 같은 오류(E3002/E3001)를 그대로 던진다. */
    public void ensureWithinBookingWindow(final LocalDateTime now) {
        final BookingWindowStatus status = bookingWindowStatus(now);
        if (status == BookingWindowStatus.BEFORE_OPEN) {
            throw new BookingNotOpenYetException();
        }
        if (status == BookingWindowStatus.CLOSED) {
            throw new PerformanceIsPastException();
        }
    }

    /** 한도가 없는 회차는 좌석 수를 제한하지 않는다. 옛 {@code BookingPolicyGuard.ensureWithinHoldLimit}와 같은 오류(E6001)를 그대로 던진다. */
    public void ensureWithinHoldLimit(final long requestedSeatCount) {
        if (holdPolicy.isOverMaxSeatCount(requestedSeatCount)) {
            throw new HoldLimitExceededException();
        }
    }

    public boolean isQueueRequired(final LocalDateTime now) {
        return queuePolicy.isRequiredAt(now, bookingWindow.getClosesAt());
    }

    public Duration holdDuration() {
        return holdPolicy.holdDuration();
    }

    public Integer maxSeatCount() {
        return holdPolicy.maxSeatCount();
    }
}
