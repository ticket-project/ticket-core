package com.ticket.core.app.error;

import com.ticket.support.error.ErrorCode;

/**
 * core-app이 소유하는 오류 코드다. 유스케이스 실행 과정에서 판단하는 실패만 담는다.
 *
 * <p>여러 모듈이 함께 쓰는 E400, E404, E500, E1000, E1001은 support:error의 CommonErrorCode가 소유한다.
 */
public enum ApplicationErrorCode implements ErrorCode {

    // 회원
    E2000("중복 이메일"),

    // 주문
    E5004("이미 진행 중인 결제 대기 주문 존재"),

    // 공연
    E7001("이미 찜한 공연"),

    // 대기열 입장
    E8000("대기열 입장 토큰 필요"),
    E8001("대기열 입장 토큰 만료"),
    E8002("대기열 입장 토큰 오류");

    private final String description;

    ApplicationErrorCode(final String description) {
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
