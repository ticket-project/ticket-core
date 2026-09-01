package com.ticket.core.domain.error;

import com.ticket.support.error.ErrorCode;

/**
 * core-domain이 소유하는 오류 코드다. 도메인 객체와 도메인 정책이 직접 판단하는 실패만 담는다.
 *
 * <p>여러 모듈이 함께 쓰는 E400, E404, E1000 같은 코드는 support:error의 CommonErrorCode가 소유한다.
 */
public enum DomainErrorCode implements ErrorCode {

    // 회차
    E3001("지난 회차"),
    E3002("예매 시작 전"),
    E3003("예매 가능한 좌석 없음"),

    // 회차 좌석
    E4000("회차 좌석 불일치"),
    E4001("이미 선택된 좌석"),
    E4002("좌석 선택 해제 권한 없음"),

    // 주문
    E5002("결제 대기 주문만 처리 가능"),
    E5003("주문 접근 권한 없음"),

    // 선점
    E6000("이미 선점된 좌석"),
    E6001("선점 가능한 좌석 수 초과"),
    E6003("선점 처리 중");

    private final String description;

    DomainErrorCode(final String description) {
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
