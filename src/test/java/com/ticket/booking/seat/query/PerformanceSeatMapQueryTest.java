package com.ticket.booking.seat.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.query.PerformanceSeatMapQuery.PerformanceSeatMapRow;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.show.Show;
import com.ticket.testsupport.persistence.ReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.Venue;

/**
 * seat-map의 booking local 조회(회차에 판매 편성된 좌석과 확정 가격)만 검증한다. 물리 좌석 좌표·등급 표시값 조합은 show 쪽 조회가 소유한다.
 *
 * <p>고정하는 것은 셋이다 — 회차가 서로 섞이지 않는 것, 편성되지 않은 물리 좌석은 나타나지 않는 것, 그리고 주문 금액의 근거가 되는 {@code unitPrice}가
 * 그대로 projection되는 것이다.
 */
@Import(PerformanceSeatMapQuery.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceSeatMapQueryTest extends ReadRepositoryTestSupport {
    @Autowired private PerformanceSeatMapQuery performanceSeatMapQuery;
    private Long performanceId;
    private Long otherPerformanceId;
    private Long seat1Id;
    private Long seat2Id;
    private Long unassignedSeatId;
    private PerformanceSeat performanceSeat1;

    @BeforeEach
    void setUp() throws Exception {
        final Venue venue = persistVenue("공연장", Region.SEOUL);
        final Show show =
                persistShow(
                        "공연",
                        venue,
                        null,
                        10L,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusDays(5));
        final Seat seat1 = persistSeat(venue, "A", "01", "01", 1);
        final Seat seat2 = persistSeat(venue, "A", "01", "02", 1);
        final Seat unassigned = persistSeat(venue, "A", "01", "03", 1);
        seat1Id = seat1.getId();
        seat2Id = seat2.getId();
        unassignedSeatId = unassigned.getId();

        final Performance performance =
                persistPerformance(show, 1L, LocalDateTime.now().plusDays(1));
        performanceId = performance.getId();
        performanceSeat1 =
                persistPerformanceSeat(
                        performance,
                        seat1,
                        PerformanceSeatState.AVAILABLE,
                        BigDecimal.valueOf(150000));
        persistPerformanceSeat(
                performance, seat2, PerformanceSeatState.RESERVED, BigDecimal.valueOf(100000));

        final Performance otherPerformance =
                persistPerformance(show, 2L, LocalDateTime.now().plusDays(2));
        otherPerformanceId = otherPerformance.getId();
        persistPerformanceSeat(
                otherPerformance, seat1, PerformanceSeatState.AVAILABLE, BigDecimal.valueOf(90000));
        flushAndClear();
    }

    @Test
    void 요청한_회차에_편성된_좌석만_반환한다() {
        final List<PerformanceSeatMapRow> rows =
                performanceSeatMapQuery.findAllByPerformanceId(performanceId);

        assertThat(rows)
                .extracting(PerformanceSeatMapRow::seatId)
                .containsExactlyInAnyOrder(seat1Id, seat2Id)
                .doesNotContain(unassignedSeatId);
    }

    @Test
    void 다른_회차의_편성은_섞이지_않는다() {
        final List<PerformanceSeatMapRow> rows =
                performanceSeatMapQuery.findAllByPerformanceId(otherPerformanceId);

        assertThat(rows).extracting(PerformanceSeatMapRow::seatId).containsExactly(seat1Id);
        assertThat(rows)
                .extracting(PerformanceSeatMapRow::unitPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.valueOf(90000));
    }

    @Test
    void 편성_식별자와_등급_확정_가격을_그대로_담는다() {
        final PerformanceSeatMapRow row =
                performanceSeatMapQuery.findAllByPerformanceId(performanceId).stream()
                        .filter(candidate -> candidate.seatId().equals(seat1Id))
                        .findFirst()
                        .orElseThrow();

        assertThat(row.performanceSeatId()).isEqualTo(performanceSeat1.getId());
        assertThat(row.performanceGradeId()).isEqualTo(1L);
        assertThat(row.unitPrice()).isEqualByComparingTo(BigDecimal.valueOf(150000));
    }

    @Test
    void 편성이_없는_회차는_빈_목록이다() {
        assertThat(performanceSeatMapQuery.findAllByPerformanceId(999_999L)).isEmpty();
    }
}
