package com.ticket.booking.seat.infrastructure;

import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.show.domain.Performance;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionAvailabilitySnapshot;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.Region;
import com.ticket.show.domain.Show;
import com.ticket.venue.domain.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(PerformanceSeatRepositoryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceSeatRepositoryAdapterSelectionTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private PerformanceSeatRepository performanceSeatRepository;

    private Long performanceId;
    private Long seatId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow(
                "뮤지컬",
                venue,
                null,
                0L,
                LocalDateTime.now(clock).minusDays(1),
                LocalDateTime.now(clock).plusDays(1)
        );
        Performance performance = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Seat seat = persistSeat(venue, "A", "10", "7", 1);
        PerformanceSeat performanceSeat = persistPerformanceSeat(
                performance,
                seat,
                PerformanceSeatState.AVAILABLE,
                BigDecimal.valueOf(120000)
        );
        performanceId = performance.getId();
        seatId = performanceSeat.getSeatId();
        flushAndClear();
    }

    @Test
    void 회차의_좌석_상태를_단건으로_조회한다() {
        SeatSelectionAvailabilitySnapshot result = performanceSeatRepository
                .findSelectableSeat(performanceId, seatId)
                .orElseThrow();

        assertThat(result.performanceSeatId()).isNotNull();
        assertThat(result.state()).isEqualTo(PerformanceSeatState.AVAILABLE);
    }

    @Test
    void 회차에_없는_좌석이면_비어있다() {
        assertThat(performanceSeatRepository.findSelectableSeat(performanceId, 999999L)).isEmpty();
    }
}
