package com.ticket.booking.seat.usecase;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;

import lombok.RequiredArgsConstructor;

/** 회차 존재 확인과 좌석 편성을 짧은 읽기 트랜잭션에서 완성한다. 이후 Redis와 show 조회는 트랜잭션 밖에서 수행한다. */
@Component
@RequiredArgsConstructor
public class SeatAvailabilitySnapshotReader {
    private final PerformanceSaleFinder performanceSaleFinder;
    private final PerformanceSeatRepository performanceSeatRepository;

    /**
     * 회차 판매 정책 조회는 회차 존재 확인을 겸한다. 예매 기간 차단은 여기서 하지 않는다 — 잔여석 조회는 접수 종료 후에도 가능해야 한다.
     *
     * @return 회차 좌석 편성. Redis 점유는 반영하지 않은 DB 시점의 상태다
     */
    @Transactional(readOnly = true)
    public List<PerformanceSeat> read(final Long performanceId) {
        performanceSaleFinder.requirePolicy(performanceId);
        return performanceSeatRepository.findAllByPerformanceId(performanceId);
    }
}
