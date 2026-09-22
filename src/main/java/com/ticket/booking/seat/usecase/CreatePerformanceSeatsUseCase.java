package com.ticket.booking.seat.usecase;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.PerformanceGradeMismatchException;
import com.ticket.booking.exception.PerformanceSeatAlreadyExistsException;
import com.ticket.booking.exception.SeatVenueMismatchException;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.api.PerformanceSaleCatalogApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * show가 공개하는 판매 좌석 편성 snapshot({@link PerformanceSaleCatalogApi})을 입력으로 받아 회차의 판매 좌석(PerformanceSeat)을 생성한다.
 *
 * <p>생성 시점에 요청한 Seat가 그 회차의 Venue에 속하는지, 요청한 PerformanceGrade가 그 회차에 속하는지를 show 공개 snapshot으로 검증하고,
 * {@code PerformanceGrade.price}를 {@code PerformanceSeat.unitPrice}로 snapshot한다. 이후 이 unitPrice는 바꾸지 않는다(고정 결정, ADR
 * 0005).
 */
@Service
@RequiredArgsConstructor
public class CreatePerformanceSeatsUseCase {
    private final PerformanceSaleCatalogApi performanceSaleCatalog;
    private final PerformanceSeatRepository performanceSeatRepository;

    /**
     * @param performanceGradeIdBySeatId 편성할 seatId -> 배정할 performanceGradeId. null·빈 map은 호출부 프로그래머 오류라
     *     {@link InvalidRequestException}으로 드러낸다.
     */
    public record Input(Long performanceId, Map<Long, Long> performanceGradeIdBySeatId) {
        public Input {
            if (performanceId == null || performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
            if (performanceGradeIdBySeatId == null || performanceGradeIdBySeatId.isEmpty()) {
                throw new InvalidRequestException("편성할 좌석이 없습니다.");
            }
        }
    }

    public record Output(List<Long> performanceSeatIds) {}

    @Transactional
    public Output execute(final Input input) {
        final Map<Long, Long> assignments = input.performanceGradeIdBySeatId();
        ensureSeatsNotAlreadyCreated(input.performanceId(), assignments.keySet());

        final PerformanceSaleSnapshot snapshot =
                performanceSaleCatalog.getSaleSnapshot(input.performanceId(), assignments.keySet());

        final List<PerformanceSeat> performanceSeats = assignments.entrySet().stream()
                .map(entry -> toPerformanceSeat(input.performanceId(), entry.getKey(), entry.getValue(), snapshot))
                .toList();

        final List<PerformanceSeat> saved = performanceSeatRepository.saveAll(performanceSeats);
        return new Output(saved.stream().map(PerformanceSeat::getId).toList());
    }

    private void ensureSeatsNotAlreadyCreated(final Long performanceId, final java.util.Set<Long> seatIds) {
        if (performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(performanceId, seatIds)
                .isEmpty()) {
            return;
        }
        throw new PerformanceSeatAlreadyExistsException(performanceId);
    }

    private PerformanceSeat toPerformanceSeat(
            final Long performanceId,
            final Long seatId,
            final Long performanceGradeId,
            final PerformanceSaleSnapshot snapshot) {
        if (!snapshot.seatInfoBySeatId().containsKey(seatId)) {
            throw new SeatVenueMismatchException(performanceId, seatId);
        }
        final PerformanceSaleSnapshot.GradeInfo gradeInfo =
                snapshot.gradeInfoByPerformanceGradeId().get(performanceGradeId);
        if (gradeInfo == null) {
            throw new PerformanceGradeMismatchException(performanceId, performanceGradeId);
        }
        return new PerformanceSeat(
                performanceId, seatId, performanceGradeId, PerformanceSeatState.AVAILABLE, gradeInfo.price());
    }
}
