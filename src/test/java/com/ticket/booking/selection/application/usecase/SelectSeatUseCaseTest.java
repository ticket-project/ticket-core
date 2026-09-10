package com.ticket.booking.selection.application.usecase;

import com.ticket.booking.seat.application.SeatStatusEvent;

import com.ticket.booking.selection.application.SeatSelectionCoordinator;

import com.ticket.booking.exception.AdmissionTokenRequiredException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.exception.SeatAlreadyHoldException;
import com.ticket.booking.salespolicy.domain.BookingEntryPolicy;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.OrderAcceptanceWindow;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.seat.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.application.SeatStatusEventPublisher;
import com.ticket.booking.selection.domain.SeatSelectionAvailabilityValidator;
import com.ticket.booking.admission.application.AdmissionVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    @Mock
    private SeatSelectionCoordinator seatSelectionCoordinator;

    @Mock
    private SeatSelectionAvailabilityValidator seatSelectionAvailabilityValidator;

    @Mock
    private AdmissionVerifier admissionVerifier;

    @Mock
    private SeatStatusEventPublisher seatEventPublisher;

    private SelectSeatUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SelectSeatUseCase(
                performanceSalesPolicyRepository,
                seatSelectionCoordinator,
                seatSelectionAvailabilityValidator,
                admissionVerifier,
                seatEventPublisher,
                CLOCK
        );
    }

    @Test
    void 정책_판정_좌석_검증_선택_발행_순서로_수행한다() {
        PerformanceSalesPolicy policy = openPolicy(false);
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(seatSelectionAvailabilityValidator.validate(10L, 20L)).thenReturn(501L);

        useCase.execute(INPUT);

        InOrder inOrder = inOrder(
                performanceSalesPolicyRepository,
                seatSelectionAvailabilityValidator,
                seatSelectionCoordinator,
                seatEventPublisher
        );
        inOrder.verify(performanceSalesPolicyRepository).findById(10L);
        inOrder.verify(seatSelectionAvailabilityValidator).validate(10L, 20L);
        inOrder.verify(seatSelectionCoordinator).select(10L, 20L, 1L, policy.getOrderAcceptanceWindow().getClosesAt());
        // 외부 판매 좌석 식별자는 seatId가 아니라 이미 검증에서 얻은 performanceSeatId다.
        inOrder.verify(seatEventPublisher).publish(10L, 501L, SeatStatusAction.SELECTED);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(false)));

        useCase.execute(INPUT);

        verify(admissionVerifier, never()).verify(10L, 1L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(true)));
        doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier).verify(10L, 1L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(seatSelectionAvailabilityValidator, seatSelectionCoordinator, seatEventPublisher);
    }

    @Test
    void 예매가_마감된_회차는_좌석을_조회하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), false)));

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(PerformanceIsPastException.class);

        verifyNoInteractions(
                seatSelectionAvailabilityValidator,
                seatSelectionCoordinator,
                seatEventPublisher,
                admissionVerifier
        );
    }

    @Test
    void 좌석_검증이_실패하면_선택하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(false)));
        doThrow(new SeatAlreadyHoldException())
                .when(seatSelectionAvailabilityValidator).validate(10L, 20L);

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(SeatAlreadyHoldException.class);

        verifyNoInteractions(seatSelectionCoordinator, seatEventPublisher);
    }

    private PerformanceSalesPolicy openPolicy(final boolean queueRequired) {
        return policy(NOW.minusHours(1), NOW.plusHours(1), queueRequired);
    }

    private PerformanceSalesPolicy policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final boolean queueRequired
    ) {
        return new PerformanceSalesPolicy(
                10L,
                new OrderAcceptanceWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(4, Duration.ofSeconds(300)),
                queueRequired
                        ? new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null)
                        : BookingEntryPolicy.none()
        );
    }
}
