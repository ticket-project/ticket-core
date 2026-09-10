package com.ticket.booking.seat.infrastructure;

import com.ticket.booking.seat.application.port.SeatAvailabilityQueryPort;

import com.ticket.booking.seat.application.port.SeatAvailabilityQueryPort;
import com.ticket.booking.seat.application.port.SeatAvailabilityQueryPort.PerformanceSeatStateRow;
import com.ticket.show.performance.domain.Performance;
import com.ticket.venue.domain.Seat;
import com.ticket.show.catalog.domain.Show;
import com.ticket.venue.Region;
import com.ticket.venue.domain.Venue;
import com.ticket.core.infra.support.ReadRepositoryTestSupport;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking local 조회만 검증한다. 등급·가격 조합은 show {@code QuerydslShowSeatMapQueryPort}가
 * 소유하고 별도로 검증한다.
 */
@Import(QuerydslSeatAvailabilityQueryPort.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslSeatAvailabilityQueryPortTest extends ReadRepositoryTestSupport {

    @Autowired
    private SeatAvailabilityQueryPort seatAvailabilityQueryPort;

    private Long performanceId;
    private Long seat1Id;
    private Long seat2Id;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("공연장", Region.SEOUL);
        Show show = persistShow("공연", venue, null, 10L, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));
        Seat seat1 = persistSeat(venue, "A", "01", "01", 1);
        Seat seat2 = persistSeat(venue, "A", "01", "02", 1);
        seat1Id = seat1.getId();
        seat2Id = seat2.getId();

        Performance performance = persistPerformance(show, 1L, LocalDateTime.now().plusDays(1));
        performanceId = performance.getId();
        persistPerformanceSeat(performance, seat2, PerformanceSeatState.RESERVED, BigDecimal.valueOf(100000));
        persistPerformanceSeat(performance, seat1, PerformanceSeatState.AVAILABLE, BigDecimal.valueOf(150000));
        flushAndClear();
    }

    @Test
    void 좌석ID순으로_회차의_판매_상태를_조회한다() {
        //when
        List<PerformanceSeatStateRow> result = seatAvailabilityQueryPort.findSeatStates(performanceId);

        //then
        assertThat(result).extracting(PerformanceSeatStateRow::seatId)
                .containsExactly(List.of(seat1Id, seat2Id).stream().sorted().toArray(Long[]::new));
        assertThat(result).extracting(PerformanceSeatStateRow::state)
                .contains(PerformanceSeatState.AVAILABLE, PerformanceSeatState.RESERVED);
    }
}
