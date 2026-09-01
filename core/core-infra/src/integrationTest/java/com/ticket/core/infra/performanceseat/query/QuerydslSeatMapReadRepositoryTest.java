package com.ticket.core.infra.performanceseat.query;

import com.ticket.core.app.performanceseat.query.SeatMapReadRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.app.performanceseat.query.model.SeatInfoView;
import com.ticket.core.app.performanceseat.query.model.SeatStateView;
import com.ticket.core.app.performanceseat.query.model.SeatStatus;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.model.ShowGrade;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslSeatMapReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslSeatMapReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private SeatMapReadRepository seatMapReadRepository;

    private Long showId;
    private Long performanceId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("venue", Region.SEOUL);
        Show show = persistShow("show", venue, null, 10L, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));
        showId = show.getId();
        ShowGrade vip = persistShowGrade(show, "VIP", "VIP", BigDecimal.valueOf(150000), 1);
        ShowGrade r = persistShowGrade(show, "R", "R", BigDecimal.valueOf(100000), 2);
        Seat seat1 = persistSeat("A", "01", "02", 1);
        Seat seat2 = persistSeat("A", "01", "01", 1);
        persistShowSeat(show, seat1, r);
        persistShowSeat(show, seat2, vip);

        Performance performance = persistPerformance(show, 1L, LocalDateTime.now().plusDays(1));
        performanceId = performance.getId();
        persistPerformanceSeat(performance, seat1, PerformanceSeatState.RESERVED, BigDecimal.valueOf(100000));
        persistPerformanceSeat(performance, seat2, PerformanceSeatState.AVAILABLE, BigDecimal.valueOf(150000));
        flushAndClear();
    }

    @Test
    void 공연_좌석_정보를_정렬해서_조회한다() {
        List<SeatInfoView> result = seatMapReadRepository.findShowSeats(showId);

        assertThat(result).extracting(SeatInfoView::seatId).hasSize(2);
        assertThat(result).extracting(SeatInfoView::gradeCode).containsExactly("VIP", "R");
        assertThat(result).extracting(SeatInfoView::col).containsExactly("01", "02");
    }

    @Test
    void 좌석별_상태를_api_상태로_변환한다() {
        List<SeatStateView> result = seatMapReadRepository.findSeatStatuses(performanceId);

        assertThat(result).containsExactly(
                new SeatStateView(result.get(0).seatId(), SeatStatus.OCCUPIED),
                new SeatStateView(result.get(1).seatId(), SeatStatus.AVAILABLE)
        );
    }
}
