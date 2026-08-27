package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.hold.store.HoldStore;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldCreationPostCommitProcessorTest {

    @Mock
    private HoldStore holdStore;

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private SeatStatusPublisher seatStatusPublisher;

    @Test
    void current_hold_releases_only_the_owners_selection_before_publishing_held() {
        final Hold hold = hold();
        when(holdStore.isHeldBy(10L, 100L, "hold-key")).thenReturn(true);
        when(holdStore.isHeldBy(10L, 200L, "hold-key")).thenReturn(true);

        processor().process(hold);

        final InOrder inOrder = inOrder(holdStore, seatSelectionService, seatStatusPublisher);
        inOrder.verify(holdStore).isHeldBy(10L, 100L, "hold-key");
        inOrder.verify(holdStore).isHeldBy(10L, 200L, "hold-key");
        inOrder.verify(seatSelectionService).deselectIfOwned(10L, 100L, 20L);
        inOrder.verify(seatSelectionService).deselectIfOwned(10L, 200L, 20L);
        inOrder.verify(seatStatusPublisher).publishHeld(10L, List.of(100L, 200L));
    }

    @Test
    void stale_hold_does_not_release_a_new_selection_or_publish_held() {
        final Hold hold = hold();
        when(holdStore.isHeldBy(10L, 100L, "hold-key")).thenReturn(false);

        processor().process(hold);

        verify(holdStore).isHeldBy(10L, 100L, "hold-key");
        verify(holdStore, never()).isHeldBy(10L, 200L, "hold-key");
        verifyNoInteractions(seatSelectionService, seatStatusPublisher);
    }

    private HoldCreationPostCommitProcessor processor() {
        return new HoldCreationPostCommitProcessor(holdStore, seatSelectionService, seatStatusPublisher);
    }

    private Hold hold() {
        return new Hold(
                "hold-key",
                20L,
                10L,
                List.of(100L, 200L),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
    }
}
