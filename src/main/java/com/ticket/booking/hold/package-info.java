/**
 * 선점(hold) capability다 — 좌석 선점 상태와 그 규칙, 선점 이력을 소유한다.
 *
 * <p>HTTP 진입점과 use case는 여기 없다. 선점은 주문 시작 workflow({@code booking.usecase})와 이벤트 후속 처리({@code
 * booking.event})가 만들고 푼다 — 파일이 없는 역할 package는 만들지 않는다.
 *
 * <p><b>Spring Modulith Application Module이 아니다.</b> booking 안에서 업무 단위로 탐색하기 위한 ordinary package다.
 */
@NullMarked
package com.ticket.booking.hold;

import org.jspecify.annotations.NullMarked;
