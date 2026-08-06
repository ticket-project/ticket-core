package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.core.domain.support.QueryRepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(SeatSelectionAvailabilityQueryRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionAvailabilityQueryRepositoryTest extends QueryRepositoryTestSupport {

    @Autowired
    private SeatSelectionAvailabilityQueryRepository repository;

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
    void 예매시간과_좌석상태를_한번에_조회한다() {
        SeatSelectionAvailabilityView result = repository
                .findForSelection(performanceId, seatId)
                .orElseThrow();

        assertThat(result.performanceSeatId()).isNotNull();
        assertThat(result.state()).isEqualTo(PerformanceSeatState.AVAILABLE);
        assertThat(result.orderOpenTime()).isBefore(LocalDateTime.now(clock));
        assertThat(result.orderCloseTime()).isAfter(LocalDateTime.now(clock));
    }

    @Test
    void 회차에_없는_좌석이어도_예매시간은_조회하고_좌석은_null이다() {
        SeatSelectionAvailabilityView result = repository
                .findForSelection(performanceId, 999999L)
                .orElseThrow();

        assertThat(result.performanceSeatId()).isNull();
        assertThat(result.state()).isNull();
    }
}
