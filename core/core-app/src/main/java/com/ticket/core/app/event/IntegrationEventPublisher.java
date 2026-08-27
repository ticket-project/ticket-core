package com.ticket.core.app.event;

import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.order.OrderTerminationResult;

import java.time.LocalDateTime;

/**
 * 커밋과 같은 트랜잭션에 기록되고, 커밋 뒤에 최소 한 번 전달되는 후속 처리 이벤트를 발행한다.
 *
 * <p>내구성 있는 저장 방식과 재시도 상태는 구현이 소유한다. app은 무슨 일이 일어났는지만 말하고
 * outbox 상태나 재시도 횟수를 직접 다루지 않는다.
 *
 * @return 발행한 이벤트의 식별자. 커밋 후 즉시 처리를 요청할 때 쓴다
 */
public interface IntegrationEventPublisher {

    Long publishHoldCreated(Hold hold, LocalDateTime occurredAt);

    Long publishHoldReleased(OrderTerminationResult result, LocalDateTime occurredAt);
}
