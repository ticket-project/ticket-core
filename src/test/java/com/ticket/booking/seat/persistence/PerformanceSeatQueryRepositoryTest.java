package com.ticket.booking.seat.persistence;

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
import com.ticket.booking.seat.persistence.PerformanceSeatQueryRepository.PerformanceSeatMapRow;
import com.ticket.booking.seat.persistence.PerformanceSeatQueryRepository.PerformanceSeatStateRow;
import com.ticket.booking.seat.query.SeatStateView;
import com.ticket.booking.seat.query.SeatStatus;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.show.Show;
import com.ticket.testsupport.persistence.ReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.Venue;

/**
 * 회차 좌석 편성의 booking local 조회 세 가지(seat-map·좌석 상태·잔여석 원본)를 한 fixture로 검증한다. 물리 좌석 좌표·등급 표시값 조합은 show
 * 쪽 조회가 소유하고 별도로 검증한다.
 *
 * <p>고정하는 것은 — 회차가 서로 섞이지 않는 것, 편성되지 않은 물리 좌석은 나타나지 않는 것, 주문 금액의 근거가 되는 {@code unitPrice}가 그대로
 * projection되는 것, DB 상태가 API 상태로 변환되는 것, 그리고 결과가 {@code seatId} 오름차순이라는 것이다.
 *
 * <p>정렬을 실제로 검증하려면 저장 순서가 {@code seatId} 오름차순이면 안 된다 — 그래서 <b>seat2를 먼저 편성한다</b>.
 */
@Import(PerformanceSeatQueryRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceSeatQueryRepositoryTest extends ReadRepositoryTestSupport {
    @Autowired private PerformanceSeatQueryRepository performanceSeatQueryRepository;
    private Long performanceId;
    private Long otherPerformanceId;
    private Long seat1Id;
    private Long seat2Id;
    private Long unassignedSeatId;
    private PerformanceSeat performanceSeat1;
    private PerformanceSeat performanceSeat2;

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
        performanceSeat2 =
                persistPerformanceSeat(
                        performance,
                        seat2,
                        PerformanceSeatState.RESERVED,
                        BigDecimal.valueOf(100000));
        performanceSeat1 =
                persistPerformanceSeat(
                        performance,
                        seat1,
                        PerformanceSeatState.AVAILABLE,
                        BigDecimal.valueOf(150000));

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
                performanceSeatQueryRepository.findAllByPerformanceId(performanceId);

        assertThat(rows)
                .extracting(PerformanceSeatMapRow::seatId)
                .containsExactlyInAnyOrder(seat1Id, seat2Id)
                .doesNotContain(unassignedSeatId);
    }

    @Test
    void 다른_회차의_편성은_섞이지_않는다() {
        final List<PerformanceSeatMapRow> rows =
                performanceSeatQueryRepository.findAllByPerformanceId(otherPerformanceId);

        assertThat(rows).extracting(PerformanceSeatMapRow::seatId).containsExactly(seat1Id);
        assertThat(rows)
                .extracting(PerformanceSeatMapRow::unitPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.valueOf(90000));
    }

    @Test
    void 편성_식별자와_등급_확정_가격을_그대로_담는다() {
        final PerformanceSeatMapRow row =
                performanceSeatQueryRepository.findAllByPerformanceId(performanceId).stream()
                        .filter(candidate -> candidate.seatId().equals(seat1Id))
                        .findFirst()
                        .orElseThrow();

        assertThat(row.performanceSeatId()).isEqualTo(performanceSeat1.getId());
        assertThat(row.performanceGradeId()).isEqualTo(1L);
        assertThat(row.unitPrice()).isEqualByComparingTo(BigDecimal.valueOf(150000));
    }

    @Test
    void 편성이_없는_회차는_빈_목록이다() {
        assertThat(performanceSeatQueryRepository.findAllByPerformanceId(999_999L)).isEmpty();
    }

    @Test
    void 좌석별_상태를_performanceSeatId_기준_api_상태로_변환한다() {
        final List<SeatStateView> result =
                performanceSeatQueryRepository.findSeatStates(performanceId);

        assertThat(result)
                .containsExactly(
                        new SeatStateView(
                                performanceSeat1.getId(),
                                performanceSeat1.getSeatId(),
                                SeatStatus.AVAILABLE),
                        new SeatStateView(
                                performanceSeat2.getId(),
                                performanceSeat2.getSeatId(),
                                SeatStatus.OCCUPIED));
    }

    @Test
    void 편성이_없는_회차는_좌석_상태도_비어_있다() {
        assertThat(performanceSeatQueryRepository.findSeatStates(999_999L)).isEmpty();
    }

    @Test
    void 좌석ID순으로_회차의_판매_상태를_조회한다() {
        final List<PerformanceSeatStateRow> result =
                performanceSeatQueryRepository.findSeatAvailabilities(performanceId);

        assertThat(result)
                .extracting(PerformanceSeatStateRow::seatId)
                .containsExactly(List.of(seat1Id, seat2Id).stream().sorted().toArray(Long[]::new));
        assertThat(result)
                .extracting(PerformanceSeatStateRow::state)
                .contains(PerformanceSeatState.AVAILABLE, PerformanceSeatState.RESERVED);
    }
}
