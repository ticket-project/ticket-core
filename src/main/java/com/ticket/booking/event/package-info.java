/**
 * 주문 이벤트 후속 처리다 — 커밋 뒤에 선점 정리·Redis 해제·좌석 상태 발행을 수행한다.
 *
 * <p>{@code HoldCreationCoordinator}와 {@code HoldReleaseCoordinator}는 이름에 Hold가 있지만 단순한 hold CRUD가 아니다. 선점·선택·판매 좌석·이벤트
 * 발행·진행 기록을 함께 조율하고, 그 workflow의 결과를 책임지는 것이 "주문 이벤트가 끝까지 처리됐는가"라 {@code booking.hold}가 아니라 여기가 소유한다.
 *
 * <p><b>공개 이벤트 {@code OrderStarted}/{@code OrderTerminated}는 여기로 옮기지 않는다.</b> 두 FQCN은 Modulith event publication
 * registry에 저장된 값이라 module root에 그대로 둔다 — 근거는 {@code com.ticket.booking}의 package-info다. listener id도 같은 이유로 옛 package
 * 문자열을 유지한다.
 */
@NullMarked
package com.ticket.booking.event;

import org.jspecify.annotations.NullMarked;
