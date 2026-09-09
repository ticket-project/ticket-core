package com.ticket.show.application;

import com.ticket.show.application.port.PerformanceSaleQueryPort;

import com.ticket.show.PerformanceSaleCatalog;
import com.ticket.show.PerformanceSaleSnapshot;
import com.ticket.show.application.port.PerformanceSaleQueryPort;
import com.ticket.show.application.port.PerformanceSaleQueryPort.PerformanceGradeRow;
import com.ticket.show.domain.PerformanceSaleContext;
import com.ticket.error.NotFoundException;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSeatAddress;
import com.ticket.venue.VenueSeatLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link PerformanceSaleCatalog}의 show 소유 구현이다. 판매 좌석 편성과 주문 표시 snapshot에
 * 필요한 회차·venue·좌석·등급 표시값을 한 번에 조회해 booking에게 scalar snapshot만 넘긴다.
 *
 * <p>venue 조합(venue 이름, 좌석 주소)은 이 application 계층이 한다 — persistence adapter
 * ({@code PerformanceSaleQueryPort}의 구현)는 show 자기 DB만 본다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceSaleCatalogService implements PerformanceSaleCatalog {

    private final PerformanceSaleQueryPort performanceSaleQueryPort;
    private final VenueLookup venueLookup;
    private final VenueSeatLookup venueSeatLookup;

    @Override
    @Transactional(readOnly = true)
    public PerformanceSaleSnapshot getSaleSnapshot(final long performanceId, final Set<Long> seatIds) {
        final PerformanceSaleContext context = performanceSaleQueryPort.findContext(performanceId)
                .orElseThrow(() -> new NotFoundException("공연을 찾을 수 없습니다. id=" + performanceId));

        final String venueName = context.venueId() == null
                ? null
                : venueLookup.findSummary(context.venueId()).map(v -> v.name()).orElse(null);

        final Map<Long, PerformanceSaleSnapshot.SeatInfo> seatInfoBySeatId = context.venueId() == null
                ? Map.of()
                : venueSeatLookup.findSeatAddresses(context.venueId(), seatIds).stream()
                        .collect(Collectors.toMap(VenueSeatAddress::seatId, this::toSeatInfo));

        final Map<Long, PerformanceSaleSnapshot.GradeInfo> gradeInfoByPerformanceGradeId =
                performanceSaleQueryPort.findPerformanceGrades(performanceId).stream()
                        .collect(Collectors.toMap(PerformanceGradeRow::performanceGradeId, this::toGradeInfo));

        return new PerformanceSaleSnapshot(
                performanceId,
                context.showId(),
                context.showTitle(),
                context.venueId(),
                venueName,
                context.performanceStartTime(),
                seatInfoBySeatId,
                gradeInfoByPerformanceGradeId
        );
    }

    private PerformanceSaleSnapshot.SeatInfo toSeatInfo(final VenueSeatAddress address) {
        return new PerformanceSaleSnapshot.SeatInfo(address.seatId(), address.floor(), address.section(), address.rowNo(), address.seatNo());
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
