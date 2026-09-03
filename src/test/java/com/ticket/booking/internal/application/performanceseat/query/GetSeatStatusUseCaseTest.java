package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.admission.internal.exception.AdmissionTokenRequiredException;
import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.exception.NotYetReserveTimeException;
import com.ticket.booking.internal.exception.PerformanceIsPastException;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateView;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStatus;
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
import java.util.Map;
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
    private BookingPolicyLookup bookingPolicyLookup;
    @Mock
    private SeatStateSnapshotReader seatStatusDbReader;
    @Mock
    private SeatSelectionService seatSelectionService;
    @Mock
    private HoldManager holdManager;
    @Mock
    private AdmissionVerifier admissionVerifier;

    private GetSeatStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSeatStatusUseCase(
                bookingPolicyLookup,
                seatStatusDbReader,
                seatSelectionService,
                holdManager,
                admissionVerifier,
                CLOCK
        );
    }

    @Test
    void redis가_점유중인_available_좌석은_occupied로_변환한다() {
        when(bookingPolicyLookup.getBookingPolicy(10L, List.of())).thenReturn(openPolicy());
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
        verify(bookingPolicyLookup).getBookingPolicy(10L, List.of());
    }

    @Test
    void redis_점유좌석이_없으면_db_상태를_그대로_반환한다() {
        List<SeatStateView> dbStates = List.of(
                new SeatStateView(1L, SeatStatus.AVAILABLE),
                new SeatStateView(2L, SeatStatus.OCCUPIED)
        );
        when(bookingPolicyLookup.getBookingPolicy(10L, List.of())).thenReturn(openPolicy());
        when(seatStatusDbReader.read(10L)).thenReturn(dbStates);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        assertThat(output.seats()).containsExactlyElementsOf(dbStates);
        verify(seatStatusDbReader).read(10L);
    }

    @Test
    void 예매가_마감된_회차는_좌석_상태를_조회하지_않는다() {
        when(bookingPolicyLookup.getBookingPolicy(10L, List.of()))
                .thenReturn(policy(NOW.minusHours(2), NOW.minusHours(1), false));

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(PerformanceIsPastException.class);

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    @Test
    void 예매가_시작되지_않은_회차는_좌석_상태를_조회하지_않는다() {
        when(bookingPolicyLookup.getBookingPolicy(10L, List.of()))
                .thenReturn(policy(NOW.plusHours(1), NOW.plusHours(2), false));

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(NotYetReserveTimeException.class);

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(bookingPolicyLookup.getBookingPolicy(10L, List.of())).thenReturn(openPolicy());
        when(seatStatusDbReader.read(10L)).thenReturn(List.of());
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        verify(admissionVerifier, never()).verify(10L, 100L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(bookingPolicyLookup.getBookingPolicy(10L, List.of())).thenReturn(queuePolicy());
        doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier).verify(10L, 100L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    private BookingPolicySnapshot openPolicy() {
        return policy(NOW.minusHours(1), NOW.plusHours(1), false);
    }

    private BookingPolicySnapshot queuePolicy() {
        return policy(NOW.minusHours(1), NOW.plusHours(1), true);
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
                queueRequired,
                Map.of()
        );
    }
}
