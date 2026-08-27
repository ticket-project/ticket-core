package com.ticket.bootstrap.worker;

import com.ticket.core.infra.order.outbox.create.HoldCreationOutboxRelay;
import com.ticket.core.infra.order.outbox.release.HoldReleaseOutboxRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 커밋 직후 즉시 처리를 놓친 후속 처리 outbox를 다시 흘려보낸다.
 *
 * <p>순수 relay이므로 유스케이스를 거치지 않고 infra relay를 직접 부른다.
 */
@Component
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class HoldOutboxRelayTrigger {

    private final HoldCreationOutboxRelay holdCreationOutboxRelay;
    private final HoldReleaseOutboxRelay holdReleaseOutboxRelay;

    @Scheduled(fixedDelayString = "${worker.hold-creation-outbox.fixed-delay:120000}")
    public void relayHoldCreations() {
        holdCreationOutboxRelay.relayPending();
    }

    @Scheduled(fixedDelayString = "${worker.hold-release-outbox.fixed-delay:120000}")
    public void relayHoldReleases() {
        holdReleaseOutboxRelay.relayPending();
    }
}
