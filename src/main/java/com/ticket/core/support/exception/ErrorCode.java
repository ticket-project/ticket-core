package com.ticket.core.support.exception;

/**
 * 클라이언트에 노출하는 전역 오류 코드다. 클라이언트는 message가 아니라 이 code로 분기한다.
 *
 * <p>{@code description}은 백엔드 개발자가 코드의 의미를 찾기 위한 내부 설명이다.
 */
public enum ErrorCode {

    E400("잘못된 요청"),
    E404("데이터 없음"),
    E500("내부 서버 오류"),

    // 인증·인가
    E1000("인증 오류"),
    E1001("인가 오류"),

    // 회원
    E2000("중복 이메일"),

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
    E5004("이미 진행 중인 결제 대기 주문 존재"),

    // 선점
    E6000("이미 선점된 좌석"),
    E6001("선점 가능한 좌석 수 초과"),
    E6003("선점 처리 중"),

    // 공연
    E7001("이미 찜한 공연"),
    E7002("미지원 공연 정렬"),

    // 대기열 입장
    E8000("대기열 입장 토큰 필요"),
    E8001("대기열 입장 토큰 만료"),
    E8002("대기열 입장 토큰 오류");

    private final String description;

    ErrorCode(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
