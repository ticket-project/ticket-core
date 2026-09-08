/**
 * 모든 Application Module이 공유하는 오류 계약을 소유한다.
 *
 * <p>여기 있는 것은 <b>어느 한 module의 것이 아닌 오류</b>뿐이다 — 프레임워크가 던지는 요청 오류
 * (E400), 어떤 module에서도 같은 뜻인 "없음"(E404), 마지막 fallback(E500), 그리고 이 셋과 module별
 * 오류를 함께 직렬화하는 {@link com.ticket.error.handler.GlobalExceptionHandler}다. 업무 의미를 가진
 * 오류는 그 업무를 소유한 module의 {@code exception}에 둔다.
 *
 * <p>이 module은 {@code web}(REST 응답 봉투)만 참조한다. 반대로 {@code web}이 이 module을
 * 참조하면 순환이 되어 {@code com.ticket.ModularityTests}가 실패한다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Error", allowedDependencies = {})
package com.ticket.error;
