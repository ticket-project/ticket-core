package com.ticket.booking.internal.infrastructure.redis;

import com.ticket.booking.internal.application.order.command.ExpireOrderUseCase;
import com.ticket.booking.internal.infrastructure.performanceseat.store.SeatSelectionRedisKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HoldKeyExpirationHandlerTest {

    @Mock
    private ExpireOrderUseCase expireOrderUseCase;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void hold_meta_키를_지원하고_주문_만료를_위임한다() {
        HoldKeyExpirationHandler handler = new HoldKeyExpirationHandler(expireOrderUseCase, fixedClock);

        String expiredKey = SeatSelectionRedisKey.holdMeta("hold-key");

        assertThat(handler.supports(expiredKey)).isTrue();

        handler.handle(expiredKey);

        verify(expireOrderUseCase).expireByHoldKey("hold-key", LocalDateTime.of(2026, 3, 15, 10, 0));
    }

    @Test
    void hold_meta_키가_아니면_지원하지_않는다() {
        HoldKeyExpirationHandler handler = new HoldKeyExpirationHandler(expireOrderUseCase, fixedClock);

        assertThat(handler.supports("unknown:key")).isFalse();
        verifyNoInteractions(expireOrderUseCase);
    }
}
