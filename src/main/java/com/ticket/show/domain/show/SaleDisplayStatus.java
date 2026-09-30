package com.ticket.show.domain.show;

/**
 * Show가 화면에 보여주는 판매 상태다. 실제 주문 접수 가능 여부는 이 값이 아니라 booking의 {@code PerformanceSalesPolicy}가 회차 단위로
 * 판단한다({@code docs/glossary.md}의 Show 정의) — 이 enum은 목록·검색·상세에 쓰이는 표시 전용 값이다.
 */
public enum SaleDisplayStatus {
    BEFORE_OPEN,
    ON_SALE,
    CLOSED
}
