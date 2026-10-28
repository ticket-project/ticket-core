package com.ticket.booking.hold.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HoldRegistry {
    private final HoldStore holdStore;

    /** 본인이 선택 중인 좌석을 선점한다. 본인 선택 확인과 충돌 확인·저장은 저장소 스크립트 하나가 한다(좌석 락 없음). */
    public Hold createHold(
            final Long memberId,
            final Long performanceId,
            final List<Long> requestedSeatIds,
            final Duration ttl,
            final LocalDateTime now) {
        final Hold hold = Hold.create(generateHoldKey(), memberId, performanceId, requestedSeatIds, now, ttl);

        return switch (holdStore.saveIfAbsent(hold, ttl)) {
            case SAVED -> hold;
            case HELD_BY_OTHER -> throw new BookingException(BookingErrorCode.E6000);
            // 같은 회원의 다른 주문 생성이 이 좌석을 먼저 잡았다(더블클릭 등). 그 주문이 진행 중이거나 이미 끝났어도 진행 중 주문 오류로 답한다.
            case HELD_BY_SAME_MEMBER -> throw new BookingException(BookingErrorCode.E5004);
            // 선택 시간이 지나 풀린 것인지(E4007)는 호출자가 실패했을 때만 따로 읽어 가른다.
            case NOT_SELECTED -> throw new BookingException(BookingErrorCode.E4006);
        };
    }

    private String generateHoldKey() {
        return "HOLD-" + UUID.randomUUID().toString().replace("-", "");
    }
}
