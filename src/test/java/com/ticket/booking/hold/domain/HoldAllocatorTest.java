package com.ticket.booking.hold.domain;

import com.ticket.booking.support.domain.RequestedSeatIds;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.domain.PerformanceSeat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class HoldAllocatorTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 11, 50);

    @Mock
    private HoldManager holdManager;

    @InjectMocks
    private HoldAllocator holdAllocator;

    @Test
    void hold를_생성하고_검증된_좌석과_함께_반환한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(3L, 7L));
        Duration holdDuration = Duration.ofMinutes(10);
        List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));
        Hold hold = new Hold(
                "hold-key",
                20L,
                10L,
                seatIds.toList(),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );

        when(holdManager.createHold(20L, 10L, seatIds, holdDuration, FIXED_NOW)).thenReturn(hold);

        HoldAllocation allocation =
                holdAllocator.allocate(20L, 10L, seatIds, seats, holdDuration, FIXED_NOW);

        assertThat(allocation.hold()).isEqualTo(hold);
        assertThat(allocation.performanceSeats()).isEqualTo(seats);
    }

    @Test
    void hold를_해제한다() {
        List<Long> seatIds = List.of(3L, 7L);
        Hold hold = new Hold(
                "hold-key",
                20L,
                10L,
                seatIds,
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
        HoldAllocation allocation = new HoldAllocation(hold, List.of(mock(PerformanceSeat.class)));

        holdAllocator.release(allocation);

        verify(holdManager).release(10L, "hold-key", seatIds);
    }
}
