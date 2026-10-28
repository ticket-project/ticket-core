package com.ticket.booking.selection.usecase;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.selection.domain.SeatSelectionStore.ExpiredSelection;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 좌석 선택 만료를 알린다. Redis 만료 알림 대신, 좌석 선택 스크립트가 선택과 함께 적어 둔 만료 장부를 꺼내 쓴다(스케일 아웃 결정 D2-2).
 *
 * <p><b>모든 인스턴스가 함께 돈다.</b> 장부에서 꺼내기와 지우기가 스크립트 한 번이라 같은 항목을 두 인스턴스가 꺼내지 않는다. 꺼낸 좌석이 지금 비어 있을 때만 알린다 — 좌석 해제·주문 생성 때 장부
 * 항목을 지우지 않으므로, 이미 해제된 좌석은 한 번 더 알리고(화면은 같다) 선점으로 넘어갔거나 다시 선택된 좌석은 알리지 않는다.
 *
 * <p>꺼낸 뒤 알리기 전에 인스턴스가 죽으면 그 알림은 빠진다. 화면 알림뿐이라 되살리는 장치를 두지 않는다.
 *
 * <p>지표: {@code booking.seat.selection.expiration.delay}는 선택 만료 시각 뒤 몇 ms 만에 장부에서 꺼냈는지다(장부가 밀리는지 본다). 임계값은 부하 측정 뒤에
 * 정한다.
 */
@Slf4j
@Service
public class NotifyExpiredSelectionsUseCase {
    private static final int BATCH_SIZE = 100;

    private final SeatSelectionService seatSelectionService;
    private final SeatSelectionWriter seatSelectionWriter;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final Timer delay;

    public NotifyExpiredSelectionsUseCase(
            final SeatSelectionService seatSelectionService,
            final SeatSelectionWriter seatSelectionWriter,
            final PerformanceSeatRepository performanceSeatRepository,
            final MeterRegistry meterRegistry) {
        this.seatSelectionService = seatSelectionService;
        this.seatSelectionWriter = seatSelectionWriter;
        this.performanceSeatRepository = performanceSeatRepository;
        this.delay = Timer.builder("booking.seat.selection.expiration.delay")
                .description("좌석 선택 만료 시각 뒤 장부에서 꺼내기까지 걸린 시간")
                .register(meterRegistry);
    }

    /** @return 이번에 꺼낸 만료 수. 꽉 채워 꺼냈으면 기다리지 않고 바로 다시 꺼낸다 */
    public int execute() {
        int popped = 0;
        while (true) {
            final List<ExpiredSelection> expired = seatSelectionService.popExpired(BATCH_SIZE);
            expired.forEach(selection -> delay.record(Duration.ofMillis(Math.max(0L, selection.delayMillis()))));
            expired.stream()
                    .collect(Collectors.groupingBy(ExpiredSelection::performanceId))
                    .forEach(this::notifyPerformance);
            popped += expired.size();
            if (expired.size() < BATCH_SIZE) {
                return popped;
            }
        }
    }

    private void notifyPerformance(final Long performanceId, final List<ExpiredSelection> expired) {
        // 알림 표시값(performanceSeatId)은 회차마다 한 번에 읽는다.
        final Map<Long, Long> performanceSeatIdBySeatId = performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(
                        performanceId,
                        expired.stream().map(ExpiredSelection::seatId).toList())
                .stream()
                .collect(Collectors.toMap(PerformanceSeat::getSeatId, PerformanceSeat::getId));
        for (final ExpiredSelection selection : expired) {
            try {
                seatSelectionWriter.notifyReleasedIfFree(
                        performanceId, selection.seatId(), performanceSeatIdBySeatId.get(selection.seatId()));
            } catch (final RuntimeException e) {
                log.warn("좌석 선택 만료 알림을 보내지 못했습니다. performanceId={}, seatId={}", performanceId, selection.seatId(), e);
            }
        }
    }
}
