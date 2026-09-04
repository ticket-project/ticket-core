package com.ticket.booking.internal.application.performanceseat.command;

import com.ticket.admission.internal.exception.AdmissionTokenRequiredException;
import com.ticket.booking.internal.exception.PerformanceIsPastException;
import com.ticket.booking.internal.exception.SeatAlreadyHoldException;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.booking.internal.domain.performanceseat.support.SeatSelectionAvailabilityValidator;
import com.ticket.admission.AdmissionVerifier;
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
import java.util.List;
import java.util.Map;

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
    private BookingPolicyLookup bookingPolicyLookup;

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
                bookingPolicyLookup,
                seatSelectionCoordinator,
                seatSelectionAvailabilityValidator,
                admissionVerifier,
                seatEventPublisher,
                CLOCK
        );
    }

    @Test
    void 정책_판정_좌석_검증_선택_발행_순서로_수행한다() {
        BookingPolicySnapshot policy = openPolicy(false);
        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(policy);

        useCase.execute(INPUT);

        InOrder inOrder = inOrder(
                bookingPolicyLookup,
                seatSelectionAvailabilityValidator,
                seatSelectionCoordinator,
                seatEventPublisher
        );
        inOrder.verify(bookingPolicyLookup).getBookingPolicy(10L);
        inOrder.verify(seatSelectionAvailabilityValidator).validate(10L, 20L);
        inOrder.verify(seatSelectionCoordinator).select(10L, 20L, 1L, policy.orderCloseTime());
        inOrder.verify(seatEventPublisher).publish(10L, 20L, SeatStatusAction.SELECTED);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(openPolicy(false));

        useCase.execute(INPUT);

        verify(admissionVerifier, never()).verify(10L, 1L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(openPolicy(true));
        doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier).verify(10L, 1L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(seatSelectionAvailabilityValidator, seatSelectionCoordinator, seatEventPublisher);
    }

    @Test
    void 예매가_마감된_회차는_좌석을_조회하지_않는다() {
        when(bookingPolicyLookup.getBookingPolicy(10L))
                .thenReturn(policy(NOW.minusHours(2), NOW.minusHours(1), false));

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
        when(bookingPolicyLookup.getBookingPolicy(10L)).thenReturn(openPolicy(false));
        doThrow(new SeatAlreadyHoldException())
                .when(seatSelectionAvailabilityValidator).validate(10L, 20L);

        assertThatThrownBy(() -> useCase.execute(INPUT))
                .isInstanceOf(SeatAlreadyHoldException.class);

        verifyNoInteractions(seatSelectionCoordinator, seatEventPublisher);
    }

    private BookingPolicySnapshot openPolicy(final boolean queueRequired) {
        return policy(NOW.minusHours(1), NOW.plusHours(1), queueRequired);
    }

    private BookingPolicySnapshot policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final boolean queueRequired
    ) {
        return new BookingPolicySnapshot(
                10L,
                1L,
                true,
                orderOpenTime,
                orderCloseTime,
                4,
                300,
                null,
                null,
                null,
                queueRequired
        );
    }
}
