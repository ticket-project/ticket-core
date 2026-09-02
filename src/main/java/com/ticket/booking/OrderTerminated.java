package com.ticket.booking;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 주문이 더 이상 활성 상태가 아니게 됐다는 사실을 알리는 도메인 이벤트다. 취소와 만료 모두 이 이벤트를
 * 발행한다. booking DB 트랜잭션(주문 상태 전이·hold 이력 저장)과 같은 트랜잭션에서 발행되고, Spring
 * Modulith의 JPA event publication registry가 커밋 뒤 최소 한 번 전달을 보장한다.
 *
 * <p>entity, lazy proxy, repository, exception은 담지 않는다. 후속 처리(Redis hold 해제, WebSocket
 * 발행)를 맡는 listener는 {@code orderId}로 현재 상태를 다시 조회해 처리한다.
 *
 * @param eventId             이 발행의 고유 식별자. listener 멱등성 판단에 쓴다
 * @param schemaVersion       이벤트 payload 스키마 버전. 초기값은 1이다
 * @param orderId             종료된 주문의 식별자
 * @param memberId            주문을 시작했던 회원의 식별자
 * @param holdKey             이 주문이 잡고 있던 hold의 key
 * @param performanceSeatIds  이 주문이 잡고 있던 PerformanceSeat 식별자 목록
 * @param reason              종료 사유. {@code CANCELED} 또는 {@code EXPIRED}
 * @param occurredAt          주문이 종료된 시각
 */
public record OrderTerminated(
        UUID eventId,
        int schemaVersion,
        long orderId,
        long memberId,
        String holdKey,
        Set<Long> performanceSeatIds,
        String reason,
        Instant occurredAt) {

    public static final int SCHEMA_VERSION = 1;

    public OrderTerminated {
        performanceSeatIds = Set.copyOf(performanceSeatIds);
    }
}
