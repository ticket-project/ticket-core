package com.ticket.security.http;

/**
 * 인증 필터가 남기고 401 entry point가 읽는 토큰 실패 사유다.
 *
 * <p>필터는 토큰이 잘못돼도 요청을 끊지 않는다 — 사유만 request attribute에 남기고 chain을 계속 태운다. 접근을 막을지는 URL별 접근 정책이 정하고(공개 API는 토큰이 무효해도 그대로
 * 통과한다), 막기로 한 경우에만 entry point가 이 사유를 읽어 401 응답의 안내 문구를 고른다.
 *
 * <p><b>attribute 이름과 사유 문자열을 양쪽이 각자 선언하고 있었다.</b> 한쪽만 고치면 안내가 조용히 기본 문구로 떨어지는데, 두 클래스의 테스트가 각자 리터럴을 쓰고 있어 그래도 전부 통과한다.
 * 그래서 값을 한곳에 둔다.
 */
final class AccessTokenFailure {
    /** 값이 바뀌면 필터와 entry point가 서로를 못 알아본다. */
    static final String REQUEST_ATTRIBUTE = "jwt.error";

    static final String EXPIRED = "expired";
    static final String INVALID = "invalid";

    private AccessTokenFailure() {}
}
