package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.model.HoldReleaseReason;
import com.ticket.core.domain.order.OrderTerminationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HoldReleaseOutboxWriterTest {

    @Mock
    private HoldReleaseOutboxRepository holdReleaseOutboxRepository;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void append_uses_clock_now_as_next_attempt_at() {
        OrderTerminationResult result = new OrderTerminationResult(1L, "hold-key", List.of(10L, 20L));
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        HoldReleaseOutbox saved = HoldReleaseOutbox.create(
                1L, "hold-key", List.of(10L, 20L), expectedNow, HoldReleaseReason.USER_CANCELED
        );
        ReflectionTestUtils.setField(saved, "id", 99L);
        when(holdReleaseOutboxRepository.save(argThat(outbox ->
                outbox.getPerformanceId().equals(1L)
                        && outbox.getHoldKey().equals("hold-key")
                        && outbox.seatIds().equals(List.of(10L, 20L))
                        && outbox.getNextAttemptAt().equals(expectedNow)
                        && outbox.getReason() == HoldReleaseReason.USER_CANCELED
        ))).thenReturn(saved);

        Long outboxId = writer().append(result, HoldReleaseReason.USER_CANCELED);

        assertThat(outboxId).isEqualTo(99L);
    }

    @Test
    void append_records_payment_confirmed_reason() {
        OrderTerminationResult result = new OrderTerminationResult(1L, "hold-key", List.of(10L));
        LocalDateTime expectedNow = LocalDateTime.of(2026, 3, 15, 10, 0);
        HoldReleaseOutbox saved = HoldReleaseOutbox.create(
                1L, "hold-key", List.of(10L), expectedNow, HoldReleaseReason.PAYMENT_CONFIRMED
        );
        ReflectionTestUtils.setField(saved, "id", 77L);
        when(holdReleaseOutboxRepository.save(argThat(outbox ->
                outbox.getReason() == HoldReleaseReason.PAYMENT_CONFIRMED
        ))).thenReturn(saved);

        Long outboxId = writer().append(result, HoldReleaseReason.PAYMENT_CONFIRMED);

        assertThat(outboxId).isEqualTo(77L);
    }

    private HoldReleaseOutboxWriter writer() {
        return new HoldReleaseOutboxWriter(holdReleaseOutboxRepository, fixedClock);
    }
}
