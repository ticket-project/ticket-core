package com.ticket.core.infra.performanceseat.query;

import com.ticket.core.app.performanceseat.query.model.AvailableSeatRow;

import com.ticket.core.app.performanceseat.query.SeatAvailabilityCalculator;
import com.ticket.core.app.performanceseat.query.SeatAvailabilityReadRepository;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.model.ShowGrade;
import com.ticket.core.domain.show.model.Region;
import com.ticket.core.domain.show.model.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslSeatAvailabilityReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslSeatAvailabilityReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private SeatAvailabilityReadRepository seatAvailabilityReadRepository;

    private Long showId;
    private Long performanceId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("공연장", Region.SEOUL);
        Show show = persistShow("공연", venue, null, 10L, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));
        showId = show.getId();
        ShowGrade vip = persistShowGrade(show, "VIP", "VIP석", BigDecimal.valueOf(150000), 1);
        ShowGrade r = persistShowGrade(show, "R", "R석", BigDecimal.valueOf(100000), 2);
        Seat seat1 = persistSeat("A", "01", "01", 1);
        Seat seat2 = persistSeat("A", "01", "02", 1);
        persistShowSeat(show, seat1, vip);
        persistShowSeat(show, seat2, r);

        Performance performance = persistPerformance(show, 1L, LocalDateTime.now().plusDays(1));
        performanceId = performance.getId();
        persistPerformanceSeat(performance, seat2, PerformanceSeatState.RESERVED, BigDecimal.valueOf(100000));
        persistPerformanceSeat(performance, seat1, PerformanceSeatState.AVAILABLE, BigDecimal.valueOf(150000));
        flushAndClear();
    }

    @Test
    void 등급정렬과_좌석ID순으로_가용좌석_원본행을_조회한다() {
        //given
        //when
        List<AvailableSeatRow> result = seatAvailabilityReadRepository.findAvailableSeatRows(performanceId, showId);

        //then
        assertThat(result).extracting("gradeName").containsExactly("VIP석", "R석");
        assertThat(result).extracting("state").containsExactly(PerformanceSeatState.AVAILABLE, PerformanceSeatState.RESERVED);
    }
}
