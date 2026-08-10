package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import com.ticket.core.support.lock.DistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldReleaseTaskProcessorTest {

    @Mock
    private HoldManager holdManager;

    @Mock
    private SeatStatusPublisher seatStatusPublisher;

    @InjectMocks
    private HoldReleaseTaskProcessor taskProcessor;

    @Test
    void publishesOnlySeatsActuallyReleasedByTheExpectedHold() {
        final HoldReleaseTask task = new HoldReleaseTask(1L, "old-hold", List.of(10L, 20L));
        when(holdManager.release(1L, "old-hold", List.of(10L, 20L))).thenReturn(List.of(10L));

        taskProcessor.process(task);

        verify(seatStatusPublisher).publishReleased(1L, List.of(10L));
    }

    @Test
    void doesNotPublishWhenSeatsBelongToANewerHold() {
        final HoldReleaseTask task = new HoldReleaseTask(1L, "old-hold", List.of(10L, 20L));
        when(holdManager.release(1L, "old-hold", List.of(10L, 20L))).thenReturn(List.of());

        taskProcessor.process(task);

        verifyNoInteractions(seatStatusPublisher);
    }

    @Test
    void holdsSeatLocksAcrossReleaseAndPublication() throws NoSuchMethodException {
        final DistributedLock lock = HoldReleaseTaskProcessor.class
                .getMethod("process", HoldReleaseTask.class)
                .getAnnotation(DistributedLock.class);

        assertThat(lock.prefix()).isEqualTo("hold");
        assertThat(lock.dynamicKey()).singleElement().asString().contains("task.seatIds");
    }
}
