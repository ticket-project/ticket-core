package com.ticket.booking.internal.infrastructure.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.SeatMapReadRepository;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateSnapshotRow;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStatus;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.seat.Seat;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.ReadRepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * booking local 조회(회차 좌석 판매 상태)만 검증한다. 물리 좌석·등급 조합은 catalog
 * {@code QuerydslShowSeatMapReadRepository}가 소유하고 별도로 검증한다.
 */
@Import(QuerydslSeatMapReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslSeatMapReadRepositoryTest extends ReadRepositoryTestSupport {

    @Autowired
    private SeatMapReadRepository seatMapReadRepository;

    private Long performanceId;
    private PerformanceSeat performanceSeat1;
    private PerformanceSeat performanceSeat2;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("venue", Region.SEOUL);
        Show show = persistShow("show", venue, null, 10L, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));
        Seat seat1 = persistSeat(venue, "A", "01", "02", 1);
        Seat seat2 = persistSeat(venue, "A", "01", "01", 1);

        Performance performance = persistPerformance(show, 1L, LocalDateTime.now().plusDays(1));
        performanceId = performance.getId();
        performanceSeat1 = persistPerformanceSeat(performance, seat1, PerformanceSeatState.RESERVED, BigDecimal.valueOf(100000));
        performanceSeat2 = persistPerformanceSeat(performance, seat2, PerformanceSeatState.AVAILABLE, BigDecimal.valueOf(150000));
        flushAndClear();
    }

    @Test
    void 좌석별_상태를_performanceSeatId_기준_api_상태로_변환한다() {
        List<SeatStateSnapshotRow> result = seatMapReadRepository.findSeatStatuses(performanceId);

        assertThat(result).containsExactly(
                new SeatStateSnapshotRow(performanceSeat1.getId(), performanceSeat1.getSeatId(), SeatStatus.OCCUPIED),
                new SeatStateSnapshotRow(performanceSeat2.getId(), performanceSeat2.getSeatId(), SeatStatus.AVAILABLE)
        );
    }
}
