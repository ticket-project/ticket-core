package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.query.model.SeatStateView;
import com.ticket.core.domain.performanceseat.query.model.SeatStatus;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetSeatStatusUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PerformanceBookingPolicyFinder performanceBookingPolicyFinder;
    @Mock
    private SeatStatusDbReader seatStatusDbReader;
    @Mock
    private SeatSelectionService seatSelectionService;
    @Mock
    private HoldManager holdManager;

    private GetSeatStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSeatStatusUseCase(
                performanceBookingPolicyFinder,
                seatStatusDbReader,
                seatSelectionService,
                holdManager,
                CLOCK
        );
    }

    @Test
    void redis가_점유중인_available_좌석은_occupied로_변환한다() {
        PerformanceBookingPolicyView policy = mock(PerformanceBookingPolicyView.class);
        when(performanceBookingPolicyFinder.findValidById(10L, NOW)).thenReturn(policy);
        when(policy.performanceId()).thenReturn(10L);
        when(seatStatusDbReader.read(10L)).thenReturn(List.of(
                new SeatStateView(1L, SeatStatus.AVAILABLE),
                new SeatStateView(2L, SeatStatus.OCCUPIED)
        ));
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L));

        assertThat(output.seats()).containsExactly(
                new SeatStateView(1L, SeatStatus.OCCUPIED),
                new SeatStateView(2L, SeatStatus.OCCUPIED)
        );
    }

    @Test
    void redis_점유좌석이_없으면_db_상태를_그대로_반환한다() {
        PerformanceBookingPolicyView policy = mock(PerformanceBookingPolicyView.class);
        List<SeatStateView> dbStates = List.of(
                new SeatStateView(1L, SeatStatus.AVAILABLE),
                new SeatStateView(2L, SeatStatus.OCCUPIED)
        );
        when(performanceBookingPolicyFinder.findValidById(10L, NOW)).thenReturn(policy);
        when(policy.performanceId()).thenReturn(10L);
        when(seatStatusDbReader.read(10L)).thenReturn(dbStates);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L));

        assertThat(output.seats()).containsExactlyElementsOf(dbStates);
        verify(seatStatusDbReader).read(10L);
    }

    @Test
    void 예매가_마감된_회차는_좌석_상태를_조회하지_않는다() {
        when(performanceBookingPolicyFinder.findValidById(10L, NOW))
                .thenThrow(new CoreException(ErrorType.PERFORMANCE_IS_PAST));

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L)))
                .isInstanceOf(CoreException.class);

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }
}
