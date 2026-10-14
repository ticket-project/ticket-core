package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.admission.AdmissionVerifier;
import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.usecase.BookingPreflightReader.Preflight;
import com.ticket.booking.salespolicy.domain.BookingWindow;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.salespolicy.domain.QueuePolicy;
import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.show.api.PerformanceSaleInfoApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

/**
 * 예매 전 확인을 고정한다. 판매 정책·입장권·회원 확인의 분기는 {@link CreateOrderUseCaseTest}가 이 reader를 실제로 조립해 관찰한다. 여기서는 진행 중 주문과 좌석 판매 상태
 * 확인, 확인 순서, 그리고 이 bean이 남은 이유인 읽기 전용 트랜잭션 계약을 본다.
 */
@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class BookingPreflightReaderTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 15, 19, 0);
    private static final long PERFORMANCE_ID = 1L;
    private static final long MEMBER_ID = 20L;
    private static final RequestedSeatIds SEAT_IDS = RequestedSeatIds.from(List.of(10L, 20L));

    @Mock
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    @Mock
    private AdmissionVerifier admissionVerifier;

    @Mock
    private MemberLookupApi memberLookupApi;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PerformanceSeatRepository performanceSeatRepository;

    @Mock
    private PerformanceSaleInfoApi performanceSaleInfoApi;

    private BookingPreflightReader reader;

    @BeforeEach
    void setUp() {
        reader = new BookingPreflightReader(
                new BookingEntryGuard(new PerformanceSaleFinder(performanceSalesPolicyRepository), admissionVerifier),
                memberLookupApi,
                orderRepository,
                performanceSeatRepository,
                performanceSaleInfoApi);
        lenient()
                .when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy()));
    }

    /** 확인과 조회를 connection 한 번으로 끝내는 것이 별도 bean으로 남은 이유다. */
    @Test
    void read는_읽기_전용_트랜잭션에서_수행한다() throws NoSuchMethodException {
        final Transactional transactional = BookingPreflightReader.class
                .getDeclaredMethod(
                        "read", Long.class, Long.class, RequestedSeatIds.class, String.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void 통과하면_정책_좌석_표시값을_돌려주고_정책_회원_주문_좌석_표시값_순서로_확인한다() {
        final List<PerformanceSeat> seats =
                List.of(seat(PerformanceSeatState.AVAILABLE), seat(PerformanceSeatState.AVAILABLE));
        final PerformanceSaleSnapshot snapshot = saleSnapshot();
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, SEAT_IDS.toList()))
                .thenReturn(seats);
        when(performanceSaleInfoApi.getSaleSnapshot(PERFORMANCE_ID, Set.copyOf(SEAT_IDS.toList())))
                .thenReturn(snapshot);

        final Preflight preflight = reader.read(MEMBER_ID, PERFORMANCE_ID, SEAT_IDS, "admission-token", NOW);

        assertThat(preflight.performanceSeats()).containsExactlyElementsOf(seats);
        assertThat(preflight.saleSnapshot()).isSameAs(snapshot);
        assertThat(preflight.policy().holdDuration()).isEqualTo(Duration.ofSeconds(600));
        final InOrder inOrder = inOrder(
                performanceSalesPolicyRepository,
                memberLookupApi,
                orderRepository,
                performanceSeatRepository,
                performanceSaleInfoApi);
        inOrder.verify(performanceSalesPolicyRepository).findById(PERFORMANCE_ID);
        inOrder.verify(memberLookupApi).requireActive(MEMBER_ID);
        inOrder.verify(orderRepository).existsPendingByMemberIdAndPerformanceId(MEMBER_ID, PERFORMANCE_ID);
        inOrder.verify(performanceSeatRepository).findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, SEAT_IDS.toList());
        inOrder.verify(performanceSaleInfoApi).getSaleSnapshot(PERFORMANCE_ID, Set.copyOf(SEAT_IDS.toList()));
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_던지고_좌석을_조회하지_않는다() {
        when(orderRepository.existsPendingByMemberIdAndPerformanceId(MEMBER_ID, PERFORMANCE_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> reader.read(MEMBER_ID, PERFORMANCE_ID, SEAT_IDS, "admission-token", NOW))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E5004);

        verifyNoInteractions(performanceSeatRepository, performanceSaleInfoApi);
    }

    @Test
    void 좌석개수가_일치하지_않으면_seatMismatch예외를_던진다() {
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, SEAT_IDS.toList()))
                .thenReturn(List.of(mock(PerformanceSeat.class)));

        assertThatThrownBy(() -> reader.read(MEMBER_ID, PERFORMANCE_ID, SEAT_IDS, "admission-token", NOW))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4000);

        verifyNoInteractions(performanceSaleInfoApi);
    }

    @Test
    void 사용불가_좌석이_포함되면_notExistAvailableSeat예외를_던진다() {
        final List<PerformanceSeat> seats =
                List.of(seat(PerformanceSeatState.AVAILABLE), seat(PerformanceSeatState.RESERVED));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, SEAT_IDS.toList()))
                .thenReturn(seats);

        assertThatThrownBy(() -> reader.read(MEMBER_ID, PERFORMANCE_ID, SEAT_IDS, "admission-token", NOW))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E3003);

        verifyNoInteractions(performanceSaleInfoApi);
    }

    private PerformanceSeat seat(final PerformanceSeatState state) {
        final PerformanceSeat performanceSeat = mock(PerformanceSeat.class);
        when(performanceSeat.getState()).thenReturn(state);
        return performanceSeat;
    }

    private PerformanceSalesPolicy openPolicy() {
        return new PerformanceSalesPolicy(
                PERFORMANCE_ID,
                new BookingWindow(NOW.minusHours(1), NOW.plusHours(3)),
                new HoldPolicy(4, Duration.ofSeconds(600)),
                QueuePolicy.none());
    }

    private PerformanceSaleSnapshot saleSnapshot() {
        return new PerformanceSaleSnapshot(
                PERFORMANCE_ID, 1L, "show-title", 1L, "venue-name", NOW.plusDays(1), Map.of(), Map.of());
    }
}
