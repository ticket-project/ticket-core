package com.ticket.booking.selection.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.selection.domain.SeatSelectionStore.ExpiredSelection;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class NotifyExpiredSelectionsUseCaseTest {
    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private SeatSelectionWriter seatSelectionWriter;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private NotifyExpiredSelectionsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new NotifyExpiredSelectionsUseCase(
                seatSelectionService, seatSelectionWriter, performanceSeatRepository, meterRegistry);
    }

    @Test
    void 꺼낸_만료마다_회차별로_한_번에_읽은_performanceSeatId로_알린다() {
        when(seatSelectionService.popExpired(100))
                .thenReturn(List.of(new ExpiredSelection(10L, 20L, 5L), new ExpiredSelection(10L, 21L, 5L)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L, 21L)))
                .thenReturn(List.of(seat(20L, 501L), seat(21L, 502L)));

        assertThat(useCase.execute()).isEqualTo(2);

        verify(seatSelectionWriter).notifyReleasedIfFree(10L, 20L, 501L);
        verify(seatSelectionWriter).notifyReleasedIfFree(10L, 21L, 502L);
        assertThat(meterRegistry
                        .get("booking.seat.selection.expiration.delay")
                        .timer()
                        .count())
                .isEqualTo(2);
    }

    /** 만료가 몰려 쌓였으면 다음 주기를 기다리지 않고 바로 다시 꺼낸다. */
    @Test
    void 꽉_채워_꺼냈으면_바로_다시_꺼낸다() {
        final List<ExpiredSelection> full = LongStream.range(0, 100)
                .mapToObj(seatId -> new ExpiredSelection(10L, seatId, 0L))
                .toList();
        when(seatSelectionService.popExpired(100)).thenReturn(full, List.of());

        assertThat(useCase.execute()).isEqualTo(100);
    }

    @Test
    void 한_좌석의_알림이_실패해도_나머지는_알린다() {
        when(seatSelectionService.popExpired(100))
                .thenReturn(List.of(new ExpiredSelection(10L, 20L, 5L), new ExpiredSelection(10L, 21L, 5L)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(10L, List.of(20L, 21L)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("redis down"))
                .when(seatSelectionWriter)
                .notifyReleasedIfFree(10L, 20L, null);

        useCase.execute();

        verify(seatSelectionWriter).notifyReleasedIfFree(10L, 21L, null);
    }

    private PerformanceSeat seat(final long seatId, final long performanceSeatId) {
        final PerformanceSeat seat =
                new PerformanceSeat(10L, seatId, 30L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
        ReflectionTestUtils.setField(seat, "id", performanceSeatId);
        return seat;
    }
}
