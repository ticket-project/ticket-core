package com.ticket.error;

/**
 * 어느 module의 것도 아닌 오류 코드다. 업무 의미가 있는 코드는 각 module의
 * {@code <Module>ErrorCode}가 소유한다.
 */
public enum CommonErrorCode implements ErrorCode {

    E400("잘못된 요청"),
    E404("데이터 없음"),
    E500("내부 서버 오류");

    private final String description;

    CommonErrorCode(final String description) {
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
