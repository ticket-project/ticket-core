/**
 * 모든 업무 모듈이 사용하는 공통 예외 계약이다.
 *
 * <p>여기 있는 것은 <b>어느 한 module의 것이 아닌 오류</b>뿐이다 — 프레임워크가 던지는 요청 오류
 * (E400), 어떤 module에서도 같은 뜻인 "없음"(E404), 마지막 fallback(E500), 그리고 이 셋과 module별
 * 오류를 함께 직렬화하는 {@link com.ticket.shared.exception.handler.GlobalExceptionHandler}다. 업무 의미를 가진
 * 오류는 그 업무를 소유한 module의 {@code exception}에 둔다.
 *
 * <p>응답 봉투는 같은 shared 모듈의 {@code web} 패키지에 있다.
 */
@org.springframework.modulith.NamedInterface("exception")
package com.ticket.shared.exception;
