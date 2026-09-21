package com.ticket.show.usecase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceSaleCatalogApi;
import com.ticket.show.api.PerformanceSaleSnapshot;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;

import lombok.RequiredArgsConstructor;

/**
 * {@link PerformanceSaleCatalogApi}의 show 소유 구현이다. 판매 좌석 편성과 주문 표시 snapshot에 필요한 회차·venue·좌석·등급
 * 표시값을 한 번에 조회해 booking에게 scalar snapshot만 넘긴다.
 *
 * <p>venue 조합(venue 이름, 좌석 주소)은 이 application 계층이 한다 — local 조회({@code PerformanceRepository})는
 * show 자기 DB만 본다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceSaleCatalogService implements PerformanceSaleCatalogApi {
    private final PerformanceRepository performanceRepository;
    private final GradeRepository gradeRepository;
    private final ShowRepository showRepository;
    private final VenueLookupApi venueLookup;
    private final VenueSeatLookupApi venueSeatLookup;

    @Override
    @Transactional(readOnly = true)
    public PerformanceSaleSnapshot getSaleSnapshot(
            final long performanceId, final Set<Long> seatIds) {
        final Performance performance =
                performanceRepository
                        .findById(performanceId)
                        .orElseThrow(
                                () -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));
        final Show show =
                showRepository
                        .findById(performance.getShowId())
                        .orElseThrow(
                                () -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));
        final Long venueId = show.getVenueId();

        final String venueName =
                venueId == null
                        ? null
                        : venueLookup.findSummary(venueId).map(v -> v.name()).orElse(null);

        final Map<Long, PerformanceSaleSnapshot.SeatInfo> seatInfoBySeatId =
                venueId == null
                        ? Map.of()
                        : venueSeatLookup.findSeats(venueId, seatIds).stream()
                                .collect(
                                        Collectors.toMap(
                                                VenueSeatLayout::seatId, this::toSeatInfo));

        final Map<Long, PerformanceSaleSnapshot.GradeInfo> gradeInfoByPerformanceGradeId =
                toGradeInfos(performanceId);

        return new PerformanceSaleSnapshot(
                performanceId,
                show.getId(),
                show.getTitle(),
                venueId,
                venueName,
                performance.getStartTime(),
                seatInfoBySeatId,
                gradeInfoByPerformanceGradeId);
    }

    /** 등급 이름을 찾지 못한 편성은 제외한다 — 옛 {@code join grade}가 그랬듯 조용히 빠진다. */
    private Map<Long, PerformanceSaleSnapshot.GradeInfo> toGradeInfos(final long performanceId) {
        final List<PerformanceGrade> performanceGrades =
                performanceRepository.findPerformanceGrades(performanceId);
        final Map<Long, Grade> gradesById =
                gradeRepository.findGradeNames(
                        performanceGrades.stream()
                                .map(PerformanceGrade::getGradeId)
                                .collect(Collectors.toSet()));

        return performanceGrades.stream()
                .filter(performanceGrade -> gradesById.containsKey(performanceGrade.getGradeId()))
                .collect(
                        Collectors.toMap(
                                PerformanceGrade::getId,
                                performanceGrade ->
                                        toGradeInfo(
                                                performanceGrade,
                                                Objects.requireNonNull(
                                                        gradesById.get(
                                                                performanceGrade.getGradeId())))));
    }

    private PerformanceSaleSnapshot.GradeInfo toGradeInfo(
            final PerformanceGrade performanceGrade, final Grade grade) {
        return new PerformanceSaleSnapshot.GradeInfo(
                performanceGrade.getId(),
                grade.getCode(),
                grade.getName(),
                performanceGrade.getSortOrder(),
                performanceGrade.getPrice());
    }

    private PerformanceSaleSnapshot.SeatInfo toSeatInfo(final VenueSeatLayout address) {
        return new PerformanceSaleSnapshot.SeatInfo(
                address.seatId(),
                address.floor(),
                address.section(),
                address.rowNo(),
                address.seatNo());
    }
}
