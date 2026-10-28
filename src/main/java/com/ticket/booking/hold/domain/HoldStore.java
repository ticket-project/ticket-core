package com.ticket.booking.hold.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.ticket.booking.seat.port.SeatStatusEvent;

/**
 * hold의 임시 상태 저장 포트다. key 형식과 TTL 적용 방식은 구현이 결정한다.
 *
 * <p>좌석 상태를 바꾸는 메서드는 확인·변경·알림 발행을 원자적으로 한다. 좌석 락 없이도 선택과 선점이 엇갈리지 않고, 알림 순서가 상태가 바뀐 순서와 같다(좌석 락 재설계 C안).
 */
public interface HoldStore {
    /** 요청한 좌석이 모두 이 회원이 지금 선택 중이고, 좌석 키가 하나도 없을 때만 모두 저장한다. 하나라도 어긋나면 아무것도 쓰지 않는다. 알림은 보내지 않는다 — 주문이 커밋되기 전이다. */
    SaveResult saveIfAbsent(Hold hold, Duration ttl);

    /**
     * 좌석이 모두 아직 이 holdKey로 잡혀 있으면, 이 회원의 선택 키를 지우고 좌석마다 {@code held}를 발행한다(주문 커밋 뒤 후처리). 하나라도 이미 풀렸으면 아무것도 하지 않는다.
     *
     * @param held 좌석마다 보낼 HELD 알림. 좌석 목록은 이 알림의 seatId다
     * @return 처리했으면 true. hold가 이미 끝났으면 false
     */
    boolean confirmHeld(Long performanceId, String holdKey, Long memberId, List<SeatStatusEvent> held);

    /**
     * 좌석에 아직 이 holdKey가 남아 있을 때만 지우고, 선택도 선점도 없는 좌석이면 좌석마다 {@code released}를 발행한다. 반복해도 그사이 다른 hold가 잡은 좌석은 건드리지 않는다.
     * hold 키가 이미 TTL로 풀린 좌석도 비어 있으면 알린다(주문 만료).
     *
     * @param released 좌석마다 보낼 RELEASED 알림. 좌석 목록은 이 알림의 seatId다
     */
    void release(Long performanceId, String holdKey, List<SeatStatusEvent> released);

    /** {@link #release}와 같지만 알리지 않는다. 주문 저장이 실패해 선점을 되돌릴 때 쓴다 — 그 좌석들은 아직 본인 선택이 남아 있어 화면이 바뀌지 않았다. */
    void releaseWithoutNotice(Long performanceId, String holdKey, List<Long> seatIds);

    Set<Long> getHoldingSeatIds(Long performanceId);

    /**
     * 선점 저장 결과다. 충돌한 좌석의 선점이 같은 회원 것이면 그 회원의 다른 주문 생성이 진행 중이거나 이미 끝난 것이다 — 선점은 주문 생성만 만든다. 회원·회차 분산 락 없이도 같은 회원의 동시 주문에
     * "진행 중인 주문" 오류를 주려고 둘을 가른다.
     */
    enum SaveResult {
        SAVED,
        HELD_BY_OTHER,
        HELD_BY_SAME_MEMBER,
        /** 요청한 좌석 중 이 회원이 지금 선택 중이 아닌 좌석이 있다(ADR 0021). */
        NOT_SELECTED
    }
}
