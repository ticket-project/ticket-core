package com.ticket.show.usecase;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.show.api.PerformanceLayoutApi;
import com.ticket.show.api.PerformanceLayoutSnapshot;
import com.ticket.show.api.PerformanceSaleInfoApi;
import com.ticket.show.api.PerformanceSaleSnapshot;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.exception.PerformanceNotFoundException;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSeatSnapshot;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * booking이 쓰는 {@link PerformanceSaleInfoApi}(판매 좌석 편성·주문 표시값)와 {@link PerformanceLayoutApi}(정적 seat-map)의 show 소유 구현이다.
 * 둘 다 회차·공연(한 번의 조인) → venue 순으로 읽고 회차 등급 표시값을 붙여 booking에게 scalar snapshot만 넘긴다.
 *
 * <p>venue 조합(venue 이름·좌석 주소·seat-map 좌표)은 이 application 계층이 한다 — local 조회({@link ShowQuerydslRepository})는 show 자기 DB만
 * 본다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceSnapshotService implements PerformanceSaleInfoApi, PerformanceLayoutApi {
    private final PerformanceRepository performanceRepository;
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookupApi;
    private final VenueSeatLookupApi venueSeatLookupApi;

    @Override
    @Transactional(readOnly = true)
    public PerformanceSaleSnapshot getSaleSnapshot(final long performanceId, final Set<Long> seatIds) {
        final PerformanceShowRow performance = requirePerformanceShow(performanceId);
        final Long venueId = performance.requireVenueId();
        final String venueName = venueLookupApi.getVenueSnapshot(venueId).name();

        final Map<Long, PerformanceSaleSnapshot.SeatInfo> seatInfoBySeatId =
                venueSeatLookupApi.findSeats(venueId, seatIds).stream()
                        .collect(Collectors.toMap(VenueSeatSnapshot::seatId, PerformanceSnapshotService::toSeatInfo));

        return new PerformanceSaleSnapshot(
                performanceId,
                performance.showId(),
                performance.title(),
                venueId,
                venueName,
                performance.startTime(),
                seatInfoBySeatId,
                mapGrades(performanceId, PerformanceSnapshotService::toGradeInfo));
    }

    @Override
    @Transactional(readOnly = true)
    public PerformanceLayoutSnapshot getVenueLayout(final long performanceId) {
        final Long venueId = requirePerformanceShow(performanceId).requireVenueId();
        final VenueSnapshot venue = venueLookupApi.getVenueSnapshot(venueId);

        final Map<Long, PerformanceLayoutSnapshot.SeatLayout> seatLayoutBySeatId =
                venueSeatLookupApi.findAllSeatLayouts(venueId).stream()
                        .collect(Collectors.toMap(VenueSeatSnapshot::seatId, PerformanceSnapshotService::toSeatLayout));

        return new PerformanceLayoutSnapshot(
                performanceId,
                venueId,
                venue.name(),
                venue.seatMapLayout().viewBoxWidth(),
                venue.seatMapLayout().viewBoxHeight(),
                venue.seatMapLayout().seatDiameter(),
                seatLayoutBySeatId,
                mapGrades(performanceId, PerformanceSnapshotService::toGradeLayout));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findRepresentativePerformanceId(final long showId) {
        return performanceRepository.findRepresentativePerformanceIdByShowId(showId);
    }

    /** 회차의 PerformanceGrade와 그 Grade다. Grade가 null이면 데이터 깨짐이다({@link #requireGrade}). */
    public record GradeRow(
            PerformanceGrade performanceGrade, @Nullable Grade grade) {}

    static Grade gradeOf(final Map<Long, Grade> gradesById, final PerformanceGrade performanceGrade) {
        return requireGrade(gradesById.get(performanceGrade.getGradeId()), performanceGrade);
    }

    /** {@code grade_id}는 NOT NULL FK({@code fk_performance_grades_grade})라 등급이 없으면 데이터 깨짐이다. 조용히 빼지 않고 여기서 터진다. */
    static Grade requireGrade(final @Nullable Grade grade, final PerformanceGrade performanceGrade) {
        return Objects.requireNonNull(
                grade,
                () -> "PerformanceGrade %d의 Grade를 찾을 수 없습니다: gradeId=%d"
                        .formatted(performanceGrade.getId(), performanceGrade.getGradeId()));
    }

    private PerformanceShowRow requirePerformanceShow(final long performanceId) {
        return showQuerydslRepository
                .findPerformanceShow(performanceId)
                .orElseThrow(() -> new PerformanceNotFoundException(performanceId));
    }

    /**
     * 회차에 배정된 PerformanceGrade를 <b>하나도 빠뜨리지 않고</b> PerformanceGrade ID로 담는다 — booking은 이 map을 완전한 것으로 보고 좌석 편성·주문 표시값을
     * 만든다.
     */
    private <T> Map<Long, T> mapGrades(final long performanceId, final BiFunction<PerformanceGrade, Grade, T> mapper) {
        return showQuerydslRepository.findPerformanceGrades(performanceId).stream()
                .collect(Collectors.toMap(
                        row -> row.performanceGrade().getId(),
                        row -> mapper.apply(
                                row.performanceGrade(), requireGrade(row.grade(), row.performanceGrade()))));
    }

    private static PerformanceSaleSnapshot.GradeInfo toGradeInfo(
            final PerformanceGrade performanceGrade, final Grade grade) {
        return new PerformanceSaleSnapshot.GradeInfo(
                performanceGrade.getId(),
                grade.getCode(),
                grade.getName(),
                performanceGrade.getSortOrder(),
                performanceGrade.getPrice());
    }

    private static PerformanceLayoutSnapshot.GradeLayout toGradeLayout(
            final PerformanceGrade performanceGrade, final Grade grade) {
        return new PerformanceLayoutSnapshot.GradeLayout(
                performanceGrade.getId(), grade.getCode(), grade.getName(), performanceGrade.getSortOrder());
    }

    private static PerformanceSaleSnapshot.SeatInfo toSeatInfo(final VenueSeatSnapshot address) {
        return new PerformanceSaleSnapshot.SeatInfo(
                address.seatId(), address.floor(), address.section(), address.rowNo(), address.seatNo());
    }

    private static PerformanceLayoutSnapshot.SeatLayout toSeatLayout(final VenueSeatSnapshot layout) {
        return new PerformanceLayoutSnapshot.SeatLayout(
                layout.seatId(),
                layout.floor(),
                layout.section(),
                layout.rowNo(),
                layout.seatNo(),
                layout.x(),
                layout.y());
    }
}
