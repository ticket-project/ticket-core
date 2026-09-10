package com.ticket.booking.exception;

import com.ticket.shared.exception.ErrorCode;

/**
 * booking module이 소유하는 오류 코드다.
 *
 * <p>E3xxx(회차 예매 정책)·E4xxx(회차 좌석)·E5xxx(주문)·E6xxx(선점)를 모두 이 module이 갖는다.
 * E3xxx는 회차 판매 정책 판정(예매 가능 여부)의 오류다 — 그 정책 데이터와 판정 모두
 * {@code booking.salespolicy.domain.PerformanceSalesPolicy}가 소유하므로 booking의
 * 오류다. 코드 값은 외부 계약이라 재번호하지 않는다.
 */
public enum BookingErrorCode implements ErrorCode {

    E3001("지난 회차"),
    E3002("예매 시작 전"),
    E3003("예매 가능한 좌석 없음"),
    E4000("회차 좌석 불일치"),
    E4001("이미 선택된 좌석"),
    E4002("좌석 선택 해제 권한 없음"),
    E4003("다른 공연장의 좌석"),
    E4004("다른 회차의 등급"),
    E4005("이미 편성된 좌석"),
    E5002("결제 대기 주문만 처리 가능"),
    E5003("주문 접근 권한 없음"),
    E5004("이미 진행 중인 결제 대기 주문 존재"),
    E6000("이미 선점된 좌석"),
    E6001("선점 가능한 좌석 수 초과"),
    E6003("선점 처리 중");

    private final String description;

    BookingErrorCode(final String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
