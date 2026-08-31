package com.ticket.core.infra.performanceseat;

import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.model.Region;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.model.Venue;
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
        Seat seat = persistSeat("A", "10", "7", 1);
        PerformanceSeat performanceSeat = persistPerformanceSeat(
                performance,
                seat,
                PerformanceSeatState.AVAILABLE,
                BigDecimal.valueOf(120000)
        );
        performanceId = performance.getId();
        seatId = performanceSeat.getSeat().getId();
        flushAndClear();
    }

    @Test
    void 회차의_좌석_상태를_단건으로_조회한다() {
        SeatSelectionAvailabilityView result = performanceSeatRepository
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
