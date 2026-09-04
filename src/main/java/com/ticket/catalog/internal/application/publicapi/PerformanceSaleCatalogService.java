package com.ticket.catalog.internal.application.publicapi;

import com.ticket.catalog.PerformanceSaleCatalog;
import com.ticket.catalog.PerformanceSaleSnapshot;
import com.ticket.catalog.internal.application.performance.query.PerformanceSaleReadRepository;
import com.ticket.catalog.internal.application.performance.query.PerformanceSaleReadRepository.PerformanceGradeRow;
import com.ticket.catalog.internal.application.performance.query.PerformanceSaleReadRepository.SeatAddressRow;
import com.ticket.catalog.internal.domain.performance.query.PerformanceSaleContext;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link PerformanceSaleCatalog}의 catalog 소유 구현이다. 판매 좌석 편성과 주문 표시 snapshot에
 * 필요한 회차·venue·좌석·등급 표시값을 한 번에 조회해 booking에게 scalar snapshot만 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceSaleCatalogService implements PerformanceSaleCatalog {

    private final PerformanceSaleReadRepository performanceSaleReadRepository;

    @Override
    @Transactional(readOnly = true)
    public PerformanceSaleSnapshot getSaleSnapshot(final long performanceId, final Set<Long> seatIds) {
        final PerformanceSaleContext context = performanceSaleReadRepository.findContext(performanceId)
                .orElseThrow(() -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));

        final Map<Long, PerformanceSaleSnapshot.SeatInfo> seatInfoBySeatId = context.venueId() == null
                ? Map.of()
                : performanceSaleReadRepository.findSeatAddresses(context.venueId(), seatIds).stream()
                        .collect(Collectors.toMap(SeatAddressRow::seatId, this::toSeatInfo));

        final Map<Long, PerformanceSaleSnapshot.GradeInfo> gradeInfoByPerformanceGradeId =
                performanceSaleReadRepository.findPerformanceGrades(performanceId).stream()
                        .collect(Collectors.toMap(PerformanceGradeRow::performanceGradeId, this::toGradeInfo));

        return new PerformanceSaleSnapshot(
                performanceId,
                context.showId(),
                context.showTitle(),
                context.venueId(),
                context.venueName(),
                context.performanceStartTime(),
                seatInfoBySeatId,
                gradeInfoByPerformanceGradeId
        );
    }

    private PerformanceSaleSnapshot.SeatInfo toSeatInfo(final SeatAddressRow row) {
        return new PerformanceSaleSnapshot.SeatInfo(row.seatId(), row.floor(), row.section(), row.rowNo(), row.seatNo());
    }

    private PerformanceSaleSnapshot.GradeInfo toGradeInfo(final PerformanceGradeRow row) {
        return new PerformanceSaleSnapshot.GradeInfo(
                row.performanceGradeId(),
                row.gradeCode(),
                row.gradeName(),
                row.sortOrder(),
                row.price()
        );
    }
}
