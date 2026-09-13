package com.ticket.booking.seat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.domain.PerformanceSeatStateSnapshot;
import com.ticket.show.catalog.domain.Show;
import com.ticket.show.performance.domain.Performance;
import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.Region;
import com.ticket.venue.facility.domain.Venue;
import com.ticket.venue.seat.domain.Seat;

@Import(PerformanceSeatRepositoryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceSeatRepositoryAdapterSelectionTest extends InfraReadRepositoryTestSupport {
    @Autowired private PerformanceSeatRepository performanceSeatRepository;
    private Long performanceId;
    private Long seatId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show =
                persistShow(
                        "뮤지컬",
                        venue,
                        null,
                        0L,
                        LocalDateTime.now(clock).minusDays(1),
                        LocalDateTime.now(clock).plusDays(1));
        Performance performance =
                persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Seat seat = persistSeat(venue, "A", "10", "7", 1);
        PerformanceSeat performanceSeat =
                persistPerformanceSeat(
                        performance,
                        seat,
                        PerformanceSeatState.AVAILABLE,
                        BigDecimal.valueOf(120000));
        performanceId = performance.getId();
        seatId = performanceSeat.getSeatId();
        flushAndClear();
    }

    @Test
    void 회차의_좌석_상태를_단건으로_조회한다() {
        PerformanceSeatStateSnapshot result =
                performanceSeatRepository.findSeatState(performanceId, seatId).orElseThrow();

        assertThat(result.performanceSeatId()).isNotNull();
        assertThat(result.state()).isEqualTo(PerformanceSeatState.AVAILABLE);
    }

    @Test
    void 회차에_없는_좌석이면_비어있다() {
        assertThat(performanceSeatRepository.findSeatState(performanceId, 999999L)).isEmpty();
    }
}
