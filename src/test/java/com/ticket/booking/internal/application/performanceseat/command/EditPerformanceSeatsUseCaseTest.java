package com.ticket.booking.internal.application.performanceseat.command;

import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.internal.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.booking.internal.exception.PerformanceGradeMismatchException;
import com.ticket.booking.internal.exception.PerformanceSeatAlreadyEditionedException;
import com.ticket.booking.internal.exception.SeatVenueMismatchException;
import com.ticket.catalog.PerformanceSaleCatalog;
import com.ticket.catalog.PerformanceSaleSnapshot;
import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class EditPerformanceSeatsUseCaseTest {

    private static final long PERFORMANCE_ID = 1L;
    private static final long SEAT_ID = 10L;
    private static final long PERFORMANCE_GRADE_ID = 100L;

    @Mock
    private PerformanceSaleCatalog performanceSaleCatalog;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @InjectMocks
    private EditPerformanceSeatsUseCase useCase;

    @Test
    void 편성할_좌석이_없으면_예외를_던진다() {
        assertThatThrownBy(() -> new EditPerformanceSeatsUseCase.Input(PERFORMANCE_ID, Map.of()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 이미_편성된_좌석이면_예외를_던지고_catalog를_조회하지_않는다() {
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, Set.of(SEAT_ID)))
                .thenReturn(List.of(mock(PerformanceSeat.class)));

        final EditPerformanceSeatsUseCase.Input input =
                new EditPerformanceSeatsUseCase.Input(PERFORMANCE_ID, Map.of(SEAT_ID, PERFORMANCE_GRADE_ID));

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(PerformanceSeatAlreadyEditionedException.class);
        verify(performanceSaleCatalog, org.mockito.Mockito.never()).getSaleSnapshot(anyLong(), anySet());
    }

    @Test
    void 다른_venue의_좌석이면_예외를_던진다() {
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, Set.of(SEAT_ID)))
                .thenReturn(List.of());
        when(performanceSaleCatalog.getSaleSnapshot(eq(PERFORMANCE_ID), anySet()))
                .thenReturn(snapshotWithoutSeat());

        final EditPerformanceSeatsUseCase.Input input =
                new EditPerformanceSeatsUseCase.Input(PERFORMANCE_ID, Map.of(SEAT_ID, PERFORMANCE_GRADE_ID));

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(SeatVenueMismatchException.class);
    }

    @Test
    void 다른_회차의_grade면_예외를_던진다() {
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, Set.of(SEAT_ID)))
                .thenReturn(List.of());
        when(performanceSaleCatalog.getSaleSnapshot(eq(PERFORMANCE_ID), anySet()))
                .thenReturn(snapshotWithSeatButNoGrade());

        final EditPerformanceSeatsUseCase.Input input =
                new EditPerformanceSeatsUseCase.Input(PERFORMANCE_ID, Map.of(SEAT_ID, PERFORMANCE_GRADE_ID));

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(PerformanceGradeMismatchException.class);
    }

    @Test
    void 유효한_요청이면_grade가격을_unitPrice로_snapshot해_생성한다() {
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, Set.of(SEAT_ID)))
                .thenReturn(List.of());
        when(performanceSaleCatalog.getSaleSnapshot(eq(PERFORMANCE_ID), anySet()))
                .thenReturn(validSnapshot());
        when(performanceSeatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final EditPerformanceSeatsUseCase.Input input =
                new EditPerformanceSeatsUseCase.Input(PERFORMANCE_ID, Map.of(SEAT_ID, PERFORMANCE_GRADE_ID));
        useCase.execute(input);

        final ArgumentCaptor<List<PerformanceSeat>> captor = ArgumentCaptor.forClass(List.class);
        verify(performanceSeatRepository).saveAll(captor.capture());
        final PerformanceSeat created = captor.getValue().get(0);
        assertThat(created.getPerformanceId()).isEqualTo(PERFORMANCE_ID);
        assertThat(created.getSeatId()).isEqualTo(SEAT_ID);
        assertThat(created.getPerformanceGradeId()).isEqualTo(PERFORMANCE_GRADE_ID);
        assertThat(created.getUnitPrice()).isEqualByComparingTo("170000");
    }

    private PerformanceSaleSnapshot snapshotWithoutSeat() {
        return new PerformanceSaleSnapshot(
                PERFORMANCE_ID, 1L, "show", 1L, "venue", null,
                Map.of(),
                Map.of(PERFORMANCE_GRADE_ID, new PerformanceSaleSnapshot.GradeInfo(PERFORMANCE_GRADE_ID, "VIP", "VIP석", 1, new BigDecimal("170000")))
        );
    }

    private PerformanceSaleSnapshot snapshotWithSeatButNoGrade() {
        return new PerformanceSaleSnapshot(
                PERFORMANCE_ID, 1L, "show", 1L, "venue", null,
                Map.of(SEAT_ID, new PerformanceSaleSnapshot.SeatInfo(SEAT_ID, 1, "가", "A", "1")),
                Map.of()
        );
    }

    private PerformanceSaleSnapshot validSnapshot() {
        return new PerformanceSaleSnapshot(
                PERFORMANCE_ID, 1L, "show", 1L, "venue", null,
                Map.of(SEAT_ID, new PerformanceSaleSnapshot.SeatInfo(SEAT_ID, 1, "가", "A", "1")),
                Map.of(PERFORMANCE_GRADE_ID, new PerformanceSaleSnapshot.GradeInfo(PERFORMANCE_GRADE_ID, "VIP", "VIP석", 1, new BigDecimal("170000")))
        );
    }
}
