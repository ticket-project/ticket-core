package com.ticket.core.app.performanceseat.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicySnapshot;
import com.ticket.core.app.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.core.app.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.core.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.core.app.admission.AdmissionGuard;
import com.ticket.core.domain.queue.model.QueueMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SelectSeatUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final SelectSeatUseCase.Input INPUT =
            new SelectSeatUseCase.Input(10L, 20L, 1L, "admission-token");

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private SeatSelectionCoordinator seatSelectionCoordinator;

    @Mock
    private SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;

    @Mock
    private AdmissionGuard admissionGuard;

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    private SelectSeatUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SelectSeatUseCase(
                performanceRepository,
                seatSelectionCoordinator,
                seatSelectionAvailabilityValidator,
                admissionGuard,
                seatEventPublisher,
                CLOCK
        );
    }

    @Test
    void 정책_판정_좌석_검증_선택_발행_순서로_수행한다() {
        PerformanceBookingPolicySnapshot policy = openPolicy(null);
        when(performanceRepository.findBookingPolicyById(10L)).thenReturn(Optional.of(policy));

        useCase.execute(INPUT);

        InOrder inOrder = inOrder(
                performanceRepository,
                seatSelectionAvailabilityValidator,
                seatSelectionCoordinator,
                seatEventPublisher
        );
        inOrder.verify(performanceRepository).findBookingPolicyById(10L);
        inOrder.verify(seatSelectionAvailabilityValidator).validate(10L, 20L);
        inOrder.verify(seatSelectionCoordinator).select(10L, 20L, 1L, policy.orderCloseTime());
        inOrder.verify(seatEventPublisher).publish(10L, 20L, SeatStatusAction.SELECTED);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(performanceRepository.findBookingPolicyById(10L)).thenReturn(Optional.of(openPolicy(QueueMode.FORCE_OFF)));

        useCase.execute(INPUT);

        verify(admissionGuard, never()).ensureAdmitted(10L, 1L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(performanceRepository.findBookingPolicyById(10L)).thenReturn(Optional.of(openPolicy(QueueMode.FORCE_ON)));
        doThrow(new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED))
                .when(admissionGuard).ensureAdmitted(10L, 1L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(CoreException.class);

        verifyNoInteractions(seatSelectionAvailabilityValidator, seatSelectionCoordinator, seatEventPublisher);
    }

    @Test
    void 예매가_마감된_회차는_좌석을_조회하지_않는다() {
        when(performanceRepository.findBookingPolicyById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), null)));

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(CoreException.class);

        verifyNoInteractions(
                seatSelectionAvailabilityValidator,
                seatSelectionCoordinator,
                seatEventPublisher,
                admissionGuard
        );
    }

    @Test
    void 좌석_검증이_실패하면_선택하지_않는다() {
        when(performanceRepository.findBookingPolicyById(10L)).thenReturn(Optional.of(openPolicy(null)));
        doThrow(new CoreException(ErrorType.SEAT_ALREADY_HOLD))
                .when(seatSelectionAvailabilityValidator).validate(10L, 20L);

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(CoreException.class);

        verifyNoInteractions(seatSelectionCoordinator, seatEventPublisher);
    }

    private PerformanceBookingPolicySnapshot openPolicy(final QueueMode queueMode) {
        return policy(NOW.minusHours(1), NOW.plusHours(1), queueMode);
    }

    private PerformanceBookingPolicySnapshot policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final QueueMode queueMode
    ) {
        return new PerformanceBookingPolicySnapshot(
                10L,
                orderOpenTime,
                orderCloseTime,
                4,
                300,
                queueMode,
                null,
                null,
                null,
                null
        );
    }
}
