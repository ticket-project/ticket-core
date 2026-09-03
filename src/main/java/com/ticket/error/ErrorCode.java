package com.ticket.error;

/**
 * 클라이언트에 노출하는 오류 코드다. 클라이언트는 message가 아니라 이 code로 분기한다.
 *
 * <p>각 module이 자기 코드를 {@code <Module>ErrorCode} enum으로 소유하고 이 interface를 구현한다.
 * 코드 값(E4001 등)은 <b>외부 계약</b>이라 module이 달라져도 재번호하지 않는다. 코드 대역이 module
 * 경계와 어긋나 보이는 곳이 있는 이유가 이것이다.
 *
 * <p>전역 유일성은 {@code com.ticket.error.ErrorCodeUniquenessTest}가 지킨다 — 분산 소유의 대가로
 * 컴파일러가 중복을 잡아주지 못하기 때문이다.
 */
public interface ErrorCode {

    /** 응답 {@code error.code}에 그대로 실리는 값이다. */
    String getCode();

    /** 백엔드 개발자가 코드의 의미를 찾기 위한 내부 설명이다. 응답에 노출되지 않는다. */
    String getDescription();
}
