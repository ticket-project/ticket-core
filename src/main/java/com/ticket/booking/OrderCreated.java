package com.ticket.booking;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * 주문이 PENDING으로 시작됐다는 사실을 알리는 도메인 이벤트다. booking DB 트랜잭션(주문·주문좌석·hold 이력 저장)과 같은 트랜잭션에서 발행되고, Spring Modulith의 JPA event
 * publication registry가 커밋 뒤 최소 한 번 전달을 보장한다.
 *
 * <p>entity, lazy proxy, repository, exception은 담지 않는다. 후속 처리(selection 정리, WebSocket 발행)에 필요한 회차와 좌석 값은 schema 2부터 함께
 * 담는다 — 주문 좌석은 생성 뒤 바뀌지 않으므로 listener가 커밋 직후 주문을 다시 읽어도 같은 값이다. 그 읽기(트랜잭션 하나, SELECT 둘)를 없애려고 담는다(gatling-test
 * capacity-log 2026-11-03).
 *
 * <p>schema 1 publication(이 변경 전에 저장돼 아직 처리되지 않은 것)에는 이 값이 없다. JSON에서 읽으면 {@code performanceId}가 null이고, listener는
 * {@code orderId}로 주문을 다시 읽는다.
 *
 * @param eventId 이 발행의 고유 식별자. listener 멱등성 판단에 쓴다
 * @param schemaVersion 이벤트 payload 스키마 버전. 회차·좌석 값을 담은 것이 2다
 * @param holdKey 이 주문이 잡은 hold의 key
 * @param performanceSeatIds 이 주문이 잡은 PerformanceSeat 식별자 목록
 * @param occurredAt hold가 시작된 시각
 * @param performanceId 주문한 회차. schema 1이면 null
 * @param seatIds 물리 좌석 id. 주문 좌석 저장 순서(OrderSeat {@code id ASC})와 같다. schema 1이면 비어 있다
 * @param performanceSeatIdBySeatId 물리 좌석 id → PerformanceSeat id. schema 1이면 비어 있다
 */
public record OrderCreated(
        UUID eventId,
        int schemaVersion,
        long orderId,
        long memberId,
        String holdKey,
        Set<Long> performanceSeatIds,
        Instant occurredAt,
        @Nullable Long performanceId,
        List<Long> seatIds,
        Map<Long, Long> performanceSeatIdBySeatId) {
    public static final int SCHEMA_VERSION = 2;

    public OrderCreated {
        performanceSeatIds = Set.copyOf(performanceSeatIds);
        // schema 1 JSON에는 아래 두 값이 없어 null로 들어온다.
        seatIds = seatIds == null ? List.of() : List.copyOf(seatIds);
        performanceSeatIdBySeatId =
                performanceSeatIdBySeatId == null ? Map.of() : Map.copyOf(performanceSeatIdBySeatId);
    }
}
