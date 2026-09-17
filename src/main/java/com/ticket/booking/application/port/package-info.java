/**
 * booking의 application이 요구하는 출력 계약이다.
 *
 * <p>조회 포트({@code *QueryPort})와 그 결과 타입뿐 아니라, 구현을 infrastructure가 고르는 다른 추상도 여기 둔다 — 입장 토큰
 * 검증({@link com.ticket.booking.admission.AdmissionVerifier}), hold 해제 완료 기록({@link
 * com.ticket.booking.application.port.HoldReleaseProgressRecorder}), 좌석 상태 발행({@link
 * com.ticket.booking.seat.port.SeatStatusEventPublisher})이 그것이다.
 *
 * <p>분산락 계약({@code LockManager})만 예외로 {@code application.concurrency}에 있다. 값 타입 셋과 한 묶음이라 떼면 계약이 두
 * package로 갈라지기 때문이다 — 이유는 그 package의 package-info에 있다.
 */
@NullMarked
package com.ticket.booking.application.port;

import org.jspecify.annotations.NullMarked;
