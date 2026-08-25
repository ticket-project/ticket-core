package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.queue.AdmissionGuard;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.domain.performanceseat.query.model.SeatStateView;
import com.ticket.core.domain.performanceseat.query.model.SeatStatus;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    @Mock
    private AdmissionGuard admissionGuard;

    private GetSeatStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSeatStatusUseCase(
                performanceBookingPolicyFinder,
                seatStatusDbReader,
                seatSelectionService,
                holdManager,
                admissionGuard,
                CLOCK
        );
    }

    @Test
    void redis가_점유중인_available_좌석은_occupied로_변환한다() {
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(openPolicy());
        when(seatStatusDbReader.read(10L)).thenReturn(List.of(
                new SeatStateView(1L, SeatStatus.AVAILABLE),
                new SeatStateView(2L, SeatStatus.OCCUPIED)
        ));
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        assertThat(output.seats()).containsExactly(
                new SeatStateView(1L, SeatStatus.OCCUPIED),
                new SeatStateView(2L, SeatStatus.OCCUPIED)
        );
        verify(performanceBookingPolicyFinder).findById(10L);
    }

    @Test
    void redis_점유좌석이_없으면_db_상태를_그대로_반환한다() {
        List<SeatStateView> dbStates = List.of(
                new SeatStateView(1L, SeatStatus.AVAILABLE),
                new SeatStateView(2L, SeatStatus.OCCUPIED)
        );
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(openPolicy());
        when(seatStatusDbReader.read(10L)).thenReturn(dbStates);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        assertThat(output.seats()).containsExactlyElementsOf(dbStates);
        verify(seatStatusDbReader).read(10L);
    }

    @Test
    void 예매가_마감된_회차는_좌석_상태를_조회하지_않는다() {
        when(performanceBookingPolicyFinder.findById(10L))
                .thenReturn(policy(NOW.minusHours(2), NOW.minusHours(1)));

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.PERFORMANCE_IS_PAST));

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    @Test
    void 예매가_시작되지_않은_회차는_좌석_상태를_조회하지_않는다() {
        when(performanceBookingPolicyFinder.findById(10L))
                .thenReturn(policy(NOW.plusHours(1), NOW.plusHours(2)));

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.NOT_YET_RESERVE_TIME));

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(openPolicy());
        when(seatStatusDbReader.read(10L)).thenReturn(List.of());
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        verify(admissionGuard, never()).ensureAdmitted(10L, 100L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(queuePolicy());
        doThrow(new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED))
                .when(admissionGuard).ensureAdmitted(10L, 100L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.ADMISSION_TOKEN_REQUIRED));

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    private PerformanceBookingPolicyView openPolicy() {
        return policy(NOW.minusHours(1), NOW.plusHours(1));
    }

    private PerformanceBookingPolicyView queuePolicy() {
        return new PerformanceBookingPolicyView(
                10L,
                NOW.minusHours(1),
                NOW.plusHours(1),
                4,
                300,
                QueueMode.FORCE_ON,
                null,
                null,
                null,
                null
        );
    }

    private PerformanceBookingPolicyView policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime
    ) {
        return new PerformanceBookingPolicyView(
                10L,
                orderOpenTime,
                orderCloseTime,
                4,
                300,
                null,
                null,
                null,
                null,
                null
        );
    }
}
