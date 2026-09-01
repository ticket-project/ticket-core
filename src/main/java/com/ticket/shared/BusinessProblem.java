package com.ticket.shared;

/**
 * 업무 module이 소유하는 오류 정보의 계약이다.
 *
 * <p>Spring Web 타입에 의존하지 않는다. 각 업무 module의 internal 오류 정의(주로 enum)가 이 계약을
 * 구현하고, 애플리케이션 루트의 전역 web adapter가 이 계약만 읽어 HTTP {@code ProblemDetail}로
 * 변환한다. 전역 handler는 이 인터페이스 이상을 알지 않는다.
 */
public interface BusinessProblem {

    /**
     * 이 문제를 소유하는 업무 module 이름이다. 예: {@code booking}.
     */
    String module();

    /**
     * module 안에서 안정적인 업무 code다. 예: {@code seat-not-available}.
     */
    String code();

    /**
     * 로그나 stack trace를 포함하지 않는 안정적인 요약이다.
     */
    String title();

    /**
     * HTTP 상태 숫자다.
     */
    int status();

    /**
     * 클라이언트에 그대로 노출 가능한 설명이다.
     */
    String detail();
}
