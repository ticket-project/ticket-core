package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.order.command.create.HoldCreationOutbox;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxRepository;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldCreationOutboxSchedulerTest {

    @Mock
    private HoldCreationOutboxRepository repository;

    @Mock
    private HoldCreationOutboxExecutor executor;

    @Test
    void processesPendingAndFailedOutboxesThatAreDue() {
        final HoldCreationOutbox outbox = outbox();
        ReflectionTestUtils.setField(outbox, "id", 99L);
        when(repository.findAllByStatusInAndNextAttemptAtLessThanEqual(any(), any(LocalDateTime.class), any()))
                .thenReturn(new SliceImpl<>(List.of(outbox)));

        scheduler().processPendingHoldCreations();

        verify(repository).findAllByStatusInAndNextAttemptAtLessThanEqual(
                argThat(statuses -> statuses.containsAll(Arrays.asList(
                        HoldCreationOutboxStatus.PENDING,
                        HoldCreationOutboxStatus.FAILED
                )) && statuses.size() == 2),
                any(LocalDateTime.class),
                any()
        );
        verify(executor).process(org.mockito.ArgumentMatchers.eq(99L), any(LocalDateTime.class));
    }

    private HoldCreationOutboxScheduler scheduler() {
        final Clock clock = Clock.fixed(Instant.parse("2026-03-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        return new HoldCreationOutboxScheduler(repository, executor, clock);
    }

    private HoldCreationOutbox outbox() {
        return HoldCreationOutbox.create(
                new Hold(
                        "hold-key",
                        20L,
                        10L,
                        List.of(100L, 200L),
                        LocalDateTime.of(2026, 3, 15, 12, 0)
                ),
                LocalDateTime.of(2026, 3, 15, 11, 50)
        );
    }
}
