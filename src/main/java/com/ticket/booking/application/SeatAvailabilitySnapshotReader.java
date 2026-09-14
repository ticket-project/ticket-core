package com.ticket.booking.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.application.port.SeatAvailabilityQueryPort;
import com.ticket.booking.application.port.SeatAvailabilityQueryPort.PerformanceSeatStateRow;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicyRepository;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * 잔여석 계산에 필요한 booking local DB 읽기만 짧은 트랜잭션 안에서 끝낸다. 회차 존재 확인과 좌석 상태 조회를 한 번에 마치고 나면, 이후의 Redis 조회와
 * show 조회는 DB connection을 쥐지 않는다.
 *
 * <p>{@link SeatStateSnapshotReader}와 같은 이유로 별도 component다 — 같은 클래스 안에서 호출하면 {@code @Transactional}
 * proxy가 적용되지 않는다(self-invocation).
 */
@Component
@RequiredArgsConstructor
public class SeatAvailabilitySnapshotReader {
    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
    private final SeatAvailabilityQueryPort seatAvailabilityQueryPort;

    /**
     * 회차 판매 정책 조회는 회차 존재 확인을 겸한다. 접수 기간 차단은 여기서 하지 않는다 — 잔여석 조회는 접수 종료 후에도 가능해야 한다.
     *
     * @return 회차 좌석의 판매 상태 행. Redis 점유는 반영하지 않은 DB 시점의 상태다
     */
    @Transactional(readOnly = true)
    public List<PerformanceSeatStateRow> read(final Long performanceId) {
        performanceSalesPolicyRepository
                .findById(performanceId)
                .orElseThrow(
                        () -> new NotFoundException("회차 판매 정책을 찾을 수 없습니다. id=" + performanceId));
        return seatAvailabilityQueryPort.findSeatStates(performanceId);
    }
}
