package com.ticket.booking.seat.application.usecase;

import com.ticket.booking.seat.application.SeatStateSnapshotReader;

import com.ticket.booking.exception.AdmissionTokenRequiredException;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.exception.NotYetReserveTimeException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.salespolicy.domain.BookingEntryPolicy;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.OrderAcceptanceWindow;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.admission.application.AdmissionVerifier;
import com.ticket.booking.seat.application.SeatStateSnapshotRow;
import com.ticket.booking.seat.application.SeatStateView;
import com.ticket.booking.seat.application.SeatStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
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
                performanceSalesPolicyRepository,
                seatStatusDbReader,
                seatSelectionService,
                holdManager,
                admissionVerifier,
                CLOCK
        );
    }

    @Test
    void redis가_점유중인_available_좌석은_occupied로_변환한다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(seatStatusDbReader.read(10L)).thenReturn(List.of(
                new SeatStateSnapshotRow(101L, 1L, SeatStatus.AVAILABLE),
                new SeatStateSnapshotRow(102L, 2L, SeatStatus.OCCUPIED)
        ));
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of(1L));
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        assertThat(output.seats()).containsExactly(
                new SeatStateView(101L, SeatStatus.OCCUPIED),
                new SeatStateView(102L, SeatStatus.OCCUPIED)
        );
        verify(performanceSalesPolicyRepository).findById(10L);
    }

    @Test
    void redis_점유좌석이_없으면_db_상태를_performanceSeatId_기준으로_그대로_반환한다() {
        List<SeatStateSnapshotRow> dbStates = List.of(
                new SeatStateSnapshotRow(101L, 1L, SeatStatus.AVAILABLE),
                new SeatStateSnapshotRow(102L, 2L, SeatStatus.OCCUPIED)
        );
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(seatStatusDbReader.read(10L)).thenReturn(dbStates);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        assertThat(output.seats()).containsExactly(
                new SeatStateView(101L, SeatStatus.AVAILABLE),
                new SeatStateView(102L, SeatStatus.OCCUPIED)
        );
        verify(seatStatusDbReader).read(10L);
    }

    @Test
    void DB에_존재하는_좌석은_상태를_생략하지_않고_그대로_노출한다() {
        // 정적 seat-map(Task 9)과 같은 PerformanceSeat 조회원본을 쓰므로, 여기 나타난 좌석을
        // 응답에서 누락시키면 클라이언트가 그 좌석을 AVAILABLE로 잘못 추정할 수 있다.
        List<SeatStateSnapshotRow> dbStates = List.of(
                new SeatStateSnapshotRow(101L, 1L, SeatStatus.AVAILABLE),
                new SeatStateSnapshotRow(102L, 2L, SeatStatus.OCCUPIED),
                new SeatStateSnapshotRow(103L, 3L, SeatStatus.AVAILABLE)
        );
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(seatStatusDbReader.read(10L)).thenReturn(dbStates);
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        GetSeatStatusUseCase.Output output = useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        assertThat(output.seats()).hasSize(dbStates.size());
        assertThat(output.seats()).extracting(SeatStateView::performanceSeatId)
                .containsExactly(101L, 102L, 103L);
    }

    @Test
    void 예매가_마감된_회차는_좌석_상태를_조회하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(2), NOW.minusHours(1), false)));

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(PerformanceIsPastException.class);

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    @Test
    void 예매가_시작되지_않은_회차는_좌석_상태를_조회하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(NOW.plusHours(1), NOW.plusHours(2), false)));

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(NotYetReserveTimeException.class);

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy()));
        when(seatStatusDbReader.read(10L)).thenReturn(List.of());
        when(seatSelectionService.getSelectingSeatIds(10L)).thenReturn(Set.of());
        when(holdManager.getHoldingSeatIds(10L)).thenReturn(Set.of());

        useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token"));

        verify(admissionVerifier, never()).verify(10L, 100L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_좌석_조회_전에_입장을_검사한다() {
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(queuePolicy()));
        doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier).verify(10L, 100L, "admission-token");

        assertThatThrownBy(() -> useCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(seatStatusDbReader, seatSelectionService, holdManager);
    }

    private PerformanceSalesPolicy openPolicy() {
        return policy(NOW.minusHours(1), NOW.plusHours(1), false);
    }

    private PerformanceSalesPolicy queuePolicy() {
        return policy(NOW.minusHours(1), NOW.plusHours(1), true);
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
