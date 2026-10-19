package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.admission.AdmissionVerifier;
import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.concurrency.RecordingDistributedLock;
import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.salespolicy.domain.BookingWindow;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.domain.QueuePolicy;
import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceSaleInfoApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

/**
 * 주문 생성 workflow 전체를 고정한다.
 *
 * <p>준비 단계(판매 정책 확인 → 입장 검사 → 회원 확인 → 좌석 확인 → 표시값 조회)는 {@link BookingPreflightReader}가 맡는다. 이 테스트는 그 reader를 실제로 조립해
 * "어떤 조건에서 어떤 {@code BookingException}이 나오고, 그때 뒤 단계로 넘어가지 않는가"를 관찰 가능한 호출로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CreateOrderUseCaseTest {
    private static final Duration HOLD_DURATION = Duration.ofSeconds(600);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);
    private static final long PERFORMANCE_ID = 10L;
    private static final long MEMBER_ID = 20L;
    private static final String ADMISSION_TOKEN = "admission-token";

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

    @Mock
    private HoldRegistry holdRegistry;

    @Mock
    private HoldStore holdStore;

    @Mock
    private SeatSelectionService seatSelectionService;

    @Mock
    private PendingOrderCreator pendingOrderCreator;

    private final RecordingDistributedLock distributedLock = new RecordingDistributedLock();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("Asia/Seoul"));
    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        createOrderUseCase =
                // 실제 collaborator를 mock repository/verifier 위에 씌운다 -- 그래야 "없으면 404"와
                // "대기열이 필요할 때만 검증"이라는 분기가 mock에 가려지지 않고 그대로 검증된다.
                new CreateOrderUseCase(
                        distributedLock,
                        new BookingPreflightReader(
                                new BookingEntryGuard(
                                        new PerformanceSaleFinder(performanceSalesPolicyRepository), admissionVerifier),
                                memberLookupApi,
                                orderRepository,
                                performanceSeatRepository,
                                performanceSaleInfoApi),
                        holdRegistry,
                        holdStore,
                        seatSelectionService,
                        pendingOrderCreator,
                        orderRepository,
                        fixedClock);
    }

    @Test
    void 중복된_좌석_ID가_있으면_예외를_던진다() {
        final CreateOrderUseCase.Input input =
                new CreateOrderUseCase.Input(PERFORMANCE_ID, List.of(3L, 1L, 3L), MEMBER_ID, ADMISSION_TOKEN);

        assertThatThrownBy(() -> createOrderUseCase.execute(input)).isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(
                performanceSalesPolicyRepository,
                admissionVerifier,
                memberLookupApi,
                orderRepository,
                performanceSeatRepository,
                performanceSaleInfoApi,
                holdRegistry,
                pendingOrderCreator);
    }

    @Test
    void 좌석_ID가_비어있으면_예외를_던진다() {
        final CreateOrderUseCase.Input input =
                new CreateOrderUseCase.Input(PERFORMANCE_ID, List.of(), MEMBER_ID, ADMISSION_TOKEN);

        assertThatThrownBy(() -> createOrderUseCase.execute(input)).isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(
                performanceSalesPolicyRepository,
                admissionVerifier,
                memberLookupApi,
                orderRepository,
                performanceSeatRepository,
                performanceSaleInfoApi,
                holdRegistry,
                pendingOrderCreator);
    }

    @Test
    void 유효한_요청이면_hold와_주문을_생성한다() {
        final CreateOrderUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = availableSeats(2);
        final Hold hold = hold(seatIds.toList());
        final PerformanceSaleSnapshot saleSnapshot = saleSnapshot();

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(5)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList()))
                .thenReturn(seats);
        when(performanceSaleInfoApi.getSaleSnapshot(PERFORMANCE_ID, Set.copyOf(seatIds.toList())))
                .thenReturn(saleSnapshot);
        when(holdRegistry.createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);
        when(pendingOrderCreator.create(MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, hold, seats, saleSnapshot))
                .thenReturn("order-key");

        final CreateOrderUseCase.Output output = createOrderUseCase.execute(input);

        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.status()).isEqualTo(OrderState.PENDING);
        assertThat(output.expiresAt()).isEqualTo(hold.expiresAt());
        assertThat(output.remainingSeconds()).isEqualTo(600L);

        final InOrder inOrder = inOrder(
                performanceSalesPolicyRepository,
                memberLookupApi,
                orderRepository,
                performanceSeatRepository,
                performanceSaleInfoApi,
                holdRegistry,
                pendingOrderCreator);
        inOrder.verify(performanceSalesPolicyRepository).findById(PERFORMANCE_ID);
        inOrder.verify(memberLookupApi).requireActive(MEMBER_ID);
        inOrder.verify(orderRepository).existsPendingByMemberIdAndPerformanceId(MEMBER_ID, PERFORMANCE_ID);
        inOrder.verify(performanceSeatRepository).findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList());
        inOrder.verify(performanceSaleInfoApi).getSaleSnapshot(PERFORMANCE_ID, Set.copyOf(seatIds.toList()));
        inOrder.verify(holdRegistry).createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW);
        inOrder.verify(pendingOrderCreator).create(MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, hold, seats, saleSnapshot);
    }

    /** 확인한 정책과 좌석은 반환되지 않고 뒤 단계의 인자로만 관찰된다. */
    @Test
    void 확인한_정책의_선점_시간과_좌석을_그대로_뒤_단계로_넘긴다() {
        final CreateOrderUseCase.Input input = input(List.of(1L, 2L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = availableSeats(2);
        final Hold hold = hold(seatIds.toList());

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(3)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList()))
                .thenReturn(seats);
        when(holdRegistry.createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);

        createOrderUseCase.execute(input);

        verify(pendingOrderCreator)
                .create(eq(MEMBER_ID), eq(PERFORMANCE_ID), eq(HOLD_DURATION), eq(hold), same(seats), any());
    }

    @Test
    void 예매가_마감된_회차는_DB_검증으로_넘어가지_않는다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(policy(3, FIXED_NOW.minusHours(2), FIXED_NOW.minusHours(1), false)));

        assertError(seatIds, BookingErrorCode.E3001);

        verifyNoInteractions(memberLookupApi, admissionVerifier, orderRepository, performanceSeatRepository);
    }

    @Test
    void 최대_선점_가능_수량을_초과하면_DB_검증으로_넘어가지_않는다() {
        final List<Long> seatIds = List.of(1L, 2L, 3L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(2)));

        assertError(seatIds, BookingErrorCode.E6001);

        verifyNoInteractions(memberLookupApi, admissionVerifier, orderRepository, performanceSeatRepository);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        final CreateOrderUseCase.Input input = input(List.of(1L, 2L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(3)));
        stubAvailableSeats(seatIds);
        stubHold(seatIds);

        createOrderUseCase.execute(input);

        verify(admissionVerifier, never()).verify(PERFORMANCE_ID, MEMBER_ID, ADMISSION_TOKEN);
    }

    @Test
    void 대기열이_필요한_회차는_입장_검사를_DB_검증보다_먼저_한다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(policy(3, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), true)));
        doThrow(new AdmissionTokenException(AdmissionErrorCode.E8000, "admission token missing"))
                .when(admissionVerifier)
                .verify(PERFORMANCE_ID, MEMBER_ID, ADMISSION_TOKEN);

        assertThatThrownBy(() -> createOrderUseCase.execute(input(seatIds)))
                .isInstanceOf(AdmissionTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", AdmissionErrorCode.E8000);

        verifyNoInteractions(memberLookupApi, orderRepository, performanceSeatRepository);
    }

    /** 인증은 토큰만 보므로 탈퇴 회원도 여기까지 온다. 좌석을 점유하기 전에 끊겨야 한다. */
    @Test
    void 탈퇴했거나_없는_회원이면_좌석을_선점하지_않는다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(3)));
        doThrow(new NotFoundException()).when(memberLookupApi).requireActive(MEMBER_ID);

        assertThatThrownBy(() -> createOrderUseCase.execute(input(seatIds))).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(
                orderRepository, performanceSeatRepository, performanceSaleInfoApi, holdRegistry, pendingOrderCreator);
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_전파한다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(3)));
        when(orderRepository.existsPendingByMemberIdAndPerformanceId(MEMBER_ID, PERFORMANCE_ID))
                .thenReturn(true);

        assertError(seatIds, BookingErrorCode.E5004);

        verifyNoInteractions(holdRegistry, pendingOrderCreator);
    }

    @Test
    void 좌석_수_한도가_없으면_요청_수량을_제한하지_않는다() {
        final CreateOrderUseCase.Input input = input(List.of(1L, 2L, 3L, 4L, 5L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(null)));
        stubAvailableSeats(seatIds);
        stubHold(seatIds);

        createOrderUseCase.execute(input);

        verify(performanceSeatRepository).findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList());
    }

    /** 선택 확인은 좌석 락 안에서 선점 직전에 한다. 거절되면 선점하지 않는다(ADR 0021). */
    @Test
    void 본인이_선택하지_않은_좌석이_섞여_있으면_선점하지_않는다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(3)));
        stubAvailableSeats(RequestedSeatIds.from(seatIds));
        doThrow(new BookingException(BookingErrorCode.E4006))
                .when(seatSelectionService)
                .requireSelectedBy(PERFORMANCE_ID, MEMBER_ID, seatIds);

        assertThatThrownBy(() -> createOrderUseCase.execute(input(seatIds)))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4006);

        verify(holdRegistry, never()).createHold(any(), any(), any(), any(), any());
        verifyNoInteractions(pendingOrderCreator);
    }

    /** 같은 회원의 앞선 주문이 커밋된 뒤 그 후처리가 선택을 지웠으면, 선택 오류가 아니라 진행 중 주문 오류다(회원·회차 락 대신). */
    @Test
    void 선택이_없는데_그사이_같은_회원의_pending_주문이_생겼으면_진행중_주문_오류다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(3)));
        when(orderRepository.existsPendingByMemberIdAndPerformanceId(MEMBER_ID, PERFORMANCE_ID))
                .thenReturn(false, true);
        stubAvailableSeats(RequestedSeatIds.from(seatIds));
        doThrow(new BookingException(BookingErrorCode.E4006))
                .when(seatSelectionService)
                .requireSelectedBy(PERFORMANCE_ID, MEMBER_ID, seatIds);

        assertError(seatIds, BookingErrorCode.E5004);

        verifyNoInteractions(pendingOrderCreator);
    }

    /** 같은 회원이 다른 좌석으로 동시에 주문하면 DB 유일 인덱스가 하나만 남기고, 진 쪽은 선점을 되돌린다. */
    @Test
    void 주문_저장이_유일_제약에_걸리면_hold를_해제하고_진행중_주문_오류다() {
        final CreateOrderUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = availableSeats(2);
        final Hold hold = hold(seatIds.toList());

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(5)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList()))
                .thenReturn(seats);
        when(holdRegistry.createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);
        when(pendingOrderCreator.create(
                        eq(MEMBER_ID), eq(PERFORMANCE_ID), eq(HOLD_DURATION), eq(hold), eq(seats), any()))
                .thenThrow(new DataIntegrityViolationException("uk_orders_pending_member_performance"));

        assertError(input.seatIds(), BookingErrorCode.E5004);

        verify(holdStore).release(PERFORMANCE_ID, "hold-key", seatIds.toList());
    }

    @Test
    void 주문_저장_트랜잭션이_실패하면_hold를_해제한다() {
        final CreateOrderUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = availableSeats(2);
        final Hold hold = hold(seatIds.toList());

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(5)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList()))
                .thenReturn(seats);
        when(holdRegistry.createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);
        when(pendingOrderCreator.create(
                        eq(MEMBER_ID), eq(PERFORMANCE_ID), eq(HOLD_DURATION), eq(hold), eq(seats), any()))
                .thenThrow(new RuntimeException("order failed"));

        assertThatThrownBy(() -> createOrderUseCase.execute(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order failed");

        verify(holdStore).release(PERFORMANCE_ID, "hold-key", seatIds.toList());
    }

    @Test
    void hold_해제에_실패해도_원래_예외를_유지한다() {
        final CreateOrderUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = availableSeats(2);
        final Hold hold = hold(seatIds.toList());
        final RuntimeException originalException = new RuntimeException("order failed");

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(openPolicy(5)));
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList()))
                .thenReturn(seats);
        when(holdRegistry.createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);
        when(pendingOrderCreator.create(
                        eq(MEMBER_ID), eq(PERFORMANCE_ID), eq(HOLD_DURATION), eq(hold), eq(seats), any()))
                .thenThrow(originalException);
        doThrow(new RuntimeException("release failed"))
                .when(holdStore)
                .release(PERFORMANCE_ID, "hold-key", seatIds.toList());

        assertThatThrownBy(() -> createOrderUseCase.execute(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order failed")
                .satisfies(exception -> {
                    assertThat(exception.getSuppressed()).hasSize(1);
                    assertThat(exception.getSuppressed()[0].getMessage()).isEqualTo("release failed");
                });
    }

    @Test
    void execute는_DB_트랜잭션을_직접_시작하지_않는다() throws NoSuchMethodException {
        assertThat(CreateOrderUseCase.class
                        .getDeclaredMethod("execute", CreateOrderUseCase.Input.class)
                        .isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    /**
     * 예매 전 확인은 {@link BookingPreflightReader}의 읽기 트랜잭션이 맡는다. 이 use case는 어느 method에서도 DB 트랜잭션을 열지 않는다 — 열면 분산락 대기·Redis
     * 왕복이 그 안에 들어가 connection을 오래 쥔다.
     */
    @Test
    void use_case의_어느_method도_트랜잭션을_직접_열지_않는다() {
        assertThat(CreateOrderUseCase.class.isAnnotationPresent(Transactional.class))
                .isFalse();
        for (final Method method : CreateOrderUseCase.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as("%s는 트랜잭션을 직접 열지 않는다", method.getName())
                    .isFalse();
        }
    }

    private void stubAvailableSeats(final RequestedSeatIds seatIds) {
        // 좌석 mock의 stub을 끝낸 뒤 저장소를 stub한다. thenReturn 인자 안에서 다른 mock을 stub하면 Mockito가 거부한다.
        final List<PerformanceSeat> seats = availableSeats(seatIds.size());
        when(performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(PERFORMANCE_ID, seatIds.toList()))
                .thenReturn(seats);
    }

    private List<PerformanceSeat> availableSeats(final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    final PerformanceSeat seat = mock(PerformanceSeat.class);
                    when(seat.getState()).thenReturn(PerformanceSeatState.AVAILABLE);
                    return seat;
                })
                .toList();
    }

    private void stubHold(final RequestedSeatIds seatIds) {
        lenient()
                .when(holdRegistry.createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold(seatIds.toList()));
    }

    private void assertError(final List<Long> seatIds, final BookingErrorCode expected) {
        assertThatThrownBy(() -> createOrderUseCase.execute(input(seatIds)))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private CreateOrderUseCase.Input input(final List<Long> seatIds) {
        return new CreateOrderUseCase.Input(PERFORMANCE_ID, seatIds, MEMBER_ID, ADMISSION_TOKEN);
    }

    private Hold hold(final List<Long> seatIds) {
        return new Hold("hold-key", MEMBER_ID, PERFORMANCE_ID, seatIds, FIXED_NOW.plusMinutes(10));
    }

    private PerformanceSalesPolicy openPolicy(final Integer maxCanHoldCount) {
        return policy(maxCanHoldCount, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), false);
    }

    private PerformanceSalesPolicy policy(
            final Integer maxCanHoldCount,
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final boolean queueRequired) {
        return new PerformanceSalesPolicy(
                PERFORMANCE_ID,
                new BookingWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(maxCanHoldCount, HOLD_DURATION),
                queueRequired ? new QueuePolicy(QueueMode.FORCE_ON, null) : QueuePolicy.none());
    }

    private PerformanceSaleSnapshot saleSnapshot() {
        return new PerformanceSaleSnapshot(
                PERFORMANCE_ID, 1L, "show-title", 1L, "venue-name", FIXED_NOW.plusDays(1), Map.of(), Map.of());
    }
}
