package com.ticket.show.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Show가 화면에 보여주는 판매 기간이다. 실제 주문 접수 가능 여부는 이 값이 아니라 booking의
 * {@code PerformanceSalesPolicy}가 회차 단위로 판단한다 — 이 값은 목록·검색·상세에 쓰이는 표시
 * 전용 데이터이고, booking의 판단과 정합성 검증 없이 독립적으로 존재한다(ADR 0007). 그래서
 * {@code /shows/{id}}가 ON_SALE이라 응답해도 주문 API는 다르게 판단할 수 있고, 이는 버그가
 * 아니라 허용된 결과다.
 *
 * <p>{@link SaleDisplayStatus} 판정은 이 타입 하나가 소유한다({@link #statusAt}) — 예전에는
 * {@code Show.getBookingStatus}와 Querydsl 조건이 각자 구현해 null 처리가 서로 달랐다(TD-12).
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisplaySaleWindow {

    @Column(name = "display_sale_starts_at")
    private LocalDateTime startsAt;

    @Column(name = "display_sale_ends_at")
    private LocalDateTime endsAt;

    public DisplaySaleWindow(final LocalDateTime startsAt, final LocalDateTime endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /**
     * null 창(시작·종료 중 하나라도 없음)은 "표시 정책이 아직 구성되지 않았다"는 뜻이라
     * {@code CLOSED}로 본다 — {@code Show.getBookingStatus}의 예전 규칙과 같다.
     */
    public SaleDisplayStatus statusAt(final LocalDateTime now) {
        if (startsAt == null || endsAt == null) {
            return SaleDisplayStatus.CLOSED;
        }
        if (now.isBefore(startsAt)) {
            return SaleDisplayStatus.BEFORE_OPEN;
        }
        if (now.isAfter(endsAt)) {
            return SaleDisplayStatus.CLOSED;
        }
        return SaleDisplayStatus.ON_SALE;
    }
}
