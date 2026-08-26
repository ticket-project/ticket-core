package com.ticket.support.error;

/**
 * 오류가 외부에 어떤 상태로 보이는지 나타낸다. Spring의 HttpStatus를 쓰지 않는 순수 Java 타입이라
 * 도메인·애플리케이션 모듈이 spring-web 없이 오류를 정의할 수 있다.
 */
public enum ErrorStatus {
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    UNPROCESSABLE_ENTITY(422),
    INTERNAL_SERVER_ERROR(500),
    SERVICE_UNAVAILABLE(503);

    private final int value;

    ErrorStatus(final int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
