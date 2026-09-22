/**
 * 대기열 입장 검증 capability다 — ticket-queue가 발급한 admission token을 검증하고 예매 진입을 통과시킨다.
 *
 * <p>파일이 일곱이고 서로만 부르므로 역할별로 나누지 않는다. 검증 계약({@code AdmissionVerifier})과 JWT 구현, 토큰 설정, 그리고 정책이 요구할 때만 검증을 거는
 * {@code AdmissionGuard}가 한 목록에서 읽힌다. 파일이 늘어 목록만으로 무엇이 무엇인지 알 수 없어지면 그때 나눈다.
 *
 * <p><b>Spring Modulith Application Module이 아니다.</b> booking 안에서 업무 단위로 탐색하기 위한 ordinary package다.
 */
@NullMarked
package com.ticket.booking.admission;

import org.jspecify.annotations.NullMarked;
