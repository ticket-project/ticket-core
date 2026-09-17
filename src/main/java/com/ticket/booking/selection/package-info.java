/**
 * 좌석 선택 capability다 — 사용자가 결제 전에 잡아 두는 임시 선택 상태를 소유한다.
 *
 * <p>판매 좌석 자체와 그 재고 상태는 {@code booking.seat}가 소유한다. 여기 있는 것은 "누가 지금 무엇을 고르고 있는가"뿐이다.
 *
 * <p><b>Spring Modulith Application Module이 아니다.</b> booking 안에서 업무 단위로 탐색하기 위한 ordinary package다.
 */
@NullMarked
package com.ticket.booking.selection;

import org.jspecify.annotations.NullMarked;
