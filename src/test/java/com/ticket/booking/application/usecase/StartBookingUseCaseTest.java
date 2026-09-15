package com.ticket.booking.application.usecase;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.application.AdmissionVerifier;
import com.ticket.booking.application.BookingAvailabilityChecker;
import com.ticket.booking.application.PendingOrderCreator;
import com.ticket.booking.application.RecordingLockManager;
import com.ticket.booking.domain.RequestedSeatIds;
import com.ticket.booking.domain.hold.Hold;
import com.ticket.booking.domain.hold.HoldManager;
import com.ticket.booking.domain.order.OrderState;
import com.ticket.booking.domain.salespolicy.BookingEntryPolicy;
import com.ticket.booking.domain.salespolicy.HoldPolicy;
import com.ticket.booking.domain.salespolicy.OrderAcceptanceWindow;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicyRepository;
import com.ticket.booking.domain.salespolicy.QueueMode;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.exception.AdmissionTokenRequiredException;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.exception.HoldLimitExceededException;
import com.ticket.booking.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.api.PerformanceSaleCatalogApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

/**
 * 예매 시작 workflow 전체를 고정한다.
 *
 * <p>옛 {@code CreateOrderPreparer}가 하던 준비 단계(판매 정책 확인 → 입장 검사 → 회원 확인 → 좌석 확인 → 표시값 조회)는 이제 이 use
 * case의 private method라 별도 테스트 대상이 없다. 그래서 옛 {@code CreateOrderPreparerTest}가 고정하던 "어떤 조건에서 어떤
 * {@code BookingException}이 나오고, 그때 뒤 단계로 넘어가지 않는가"를 여기서 관찰 가능한 행동으로 이어받는다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class StartBookingUseCaseTest {
    private static final Duration HOLD_DURATION = Duration.ofSeconds(600);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);
    @Mock private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
    @Mock private AdmissionVerifier admissionVerifier;
    @Mock private MemberLookupApi memberLookup;
    @Mock private BookingAvailabilityChecker bookingAvailabilityChecker;
    @Mock private PerformanceSaleCatalogApi performanceSaleCatalog;
    @Mock private HoldManager holdManager;
    @Mock private PendingOrderCreator pendingOrderCreator;
    private final RecordingLockManager lockManager = new RecordingLockManager();
    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("Asia/Seoul"));
    private StartBookingUseCase startBookingUseCase;

    @BeforeEach
    void setUp() {
        startBookingUseCase =
                new StartBookingUseCase(
                        lockManager,
                        performanceSalesPolicyRepository,
                        admissionVerifier,
                        memberLookup,
                        bookingAvailabilityChecker,
                        performanceSaleCatalog,
                        holdManager,
                        pendingOrderCreator,
                        fixedClock);
    }

    @Test
    void 중복된_좌석_ID가_있으면_예외를_던진다() {
        final StartBookingUseCase.Input input =
                new StartBookingUseCase.Input(10L, List.of(3L, 1L, 3L), 20L, "admission-token");

        assertThatThrownBy(() -> startBookingUseCase.execute(input))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(
                performanceSalesPolicyRepository,
                admissionVerifier,
                memberLookup,
                bookingAvailabilityChecker,
                performanceSaleCatalog,
                holdManager,
                pendingOrderCreator);
    }

    @Test
    void 좌석_ID가_비어있으면_예외를_던진다() {
        final StartBookingUseCase.Input input =
                new StartBookingUseCase.Input(10L, List.of(), 20L, "admission-token");

        assertThatThrownBy(() -> startBookingUseCase.execute(input))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(
                performanceSalesPolicyRepository,
                admissionVerifier,
                memberLookup,
                bookingAvailabilityChecker,
                performanceSaleCatalog,
                holdManager,
                pendingOrderCreator);
    }

    @Test
    void 유효한_요청이면_hold와_주문을_생성한다() {
        final StartBookingUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats =
                List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());
        final PerformanceSaleSnapshot saleSnapshot = saleSnapshot();

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(5)));
        when(bookingAvailabilityChecker.check(20L, 10L, seatIds)).thenReturn(seats);
        when(performanceSaleCatalog.getSaleSnapshot(10L, Set.copyOf(seatIds.toList())))
                .thenReturn(saleSnapshot);
        when(holdManager.createHold(20L, 10L, seatIds, HOLD_DURATION, FIXED_NOW)).thenReturn(hold);
        when(pendingOrderCreator.create(20L, 10L, HOLD_DURATION, hold, seats, saleSnapshot))
                .thenReturn("order-key");

        final StartBookingUseCase.Output output = startBookingUseCase.execute(input);

        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.status()).isEqualTo(OrderState.PENDING);
        assertThat(output.expiresAt()).isEqualTo(hold.expiresAt());
        assertThat(output.remainingSeconds()).isEqualTo(600L);

        // 옛 preparer.prepare 한 번의 검증이 이제 이 순서의 관찰 가능한 호출들로 대체된다.
        final InOrder inOrder =
                inOrder(
                        performanceSalesPolicyRepository,
                        memberLookup,
                        bookingAvailabilityChecker,
                        performanceSaleCatalog,
                        holdManager,
                        pendingOrderCreator);
        inOrder.verify(performanceSalesPolicyRepository).findById(10L);
        inOrder.verify(memberLookup).requireActive(20L);
        inOrder.verify(bookingAvailabilityChecker).check(20L, 10L, seatIds);
        inOrder.verify(performanceSaleCatalog).getSaleSnapshot(10L, Set.copyOf(seatIds.toList()));
        inOrder.verify(holdManager).createHold(20L, 10L, seatIds, HOLD_DURATION, FIXED_NOW);
        inOrder.verify(pendingOrderCreator)
                .create(20L, 10L, HOLD_DURATION, hold, seats, saleSnapshot);
    }

    /** 옛 {@code CreateOrderPreparerTest}의 "정책과 좌석을 함께 반환한다" — 이제 그 둘은 뒤 단계 인자로만 관찰된다. */
    @Test
    void 확인한_정책의_선점_시간과_좌석을_그대로_뒤_단계로_넘긴다() {
        final StartBookingUseCase.Input input = input(List.of(1L, 2L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats =
                List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(3)));
        when(bookingAvailabilityChecker.check(20L, 10L, seatIds)).thenReturn(seats);
        when(holdManager.createHold(20L, 10L, seatIds, HOLD_DURATION, FIXED_NOW)).thenReturn(hold);

        startBookingUseCase.execute(input);

        verify(memberLookup).requireActive(20L);
        verify(pendingOrderCreator)
                .create(eq(20L), eq(10L), eq(HOLD_DURATION), eq(hold), same(seats), any());
    }

    @Test
    void 예매가_마감된_회차는_DB_검증으로_넘어가지_않는다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                policy(
                                        3,
                                        FIXED_NOW.minusHours(2),
                                        FIXED_NOW.minusHours(1),
                                        false)));

        assertError(seatIds, PerformanceIsPastException.class);

        verifyNoInteractions(memberLookup, admissionVerifier, bookingAvailabilityChecker);
    }

    @Test
    void 최대_선점_가능_수량을_초과하면_DB_검증으로_넘어가지_않는다() {
        final List<Long> seatIds = List.of(1L, 2L, 3L);
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(2)));

        assertError(seatIds, HoldLimitExceededException.class);

        verifyNoInteractions(memberLookup, admissionVerifier, bookingAvailabilityChecker);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        final StartBookingUseCase.Input input = input(List.of(1L, 2L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(3)));
        when(bookingAvailabilityChecker.check(20L, 10L, seatIds)).thenReturn(List.of());
        stubHold(seatIds);

        startBookingUseCase.execute(input);

        verify(admissionVerifier, never()).verify(10L, 20L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_입장_검사를_DB_검증보다_먼저_한다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(
                        Optional.of(
                                policy(3, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), true)));
        doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier)
                .verify(10L, 20L, "admission-token");

        assertThatThrownBy(() -> startBookingUseCase.execute(input(seatIds)))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(memberLookup, bookingAvailabilityChecker);
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_전파한다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(3)));
        when(bookingAvailabilityChecker.check(20L, 10L, RequestedSeatIds.from(seatIds)))
                .thenThrow(new PendingOrderAlreadyExistsException(20L, 10L));

        assertError(seatIds, PendingOrderAlreadyExistsException.class);

        verifyNoInteractions(holdManager, pendingOrderCreator);
    }

    @Test
    void 좌석_수_한도가_없으면_요청_수량을_제한하지_않는다() {
        final StartBookingUseCase.Input input = input(List.of(1L, 2L, 3L, 4L, 5L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(openPolicy(null)));
        when(bookingAvailabilityChecker.check(20L, 10L, seatIds)).thenReturn(List.of());
        stubHold(seatIds);

        startBookingUseCase.execute(input);

        verify(bookingAvailabilityChecker).check(20L, 10L, seatIds);
    }

    @Test
    void 주문_저장_트랜잭션이_실패하면_hold를_해제한다() {
        final StartBookingUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(5)));
        when(bookingAvailabilityChecker.check(20L, 10L, seatIds)).thenReturn(seats);
        when(holdManager.createHold(20L, 10L, seatIds, HOLD_DURATION, FIXED_NOW)).thenReturn(hold);
        when(pendingOrderCreator.create(
                        eq(20L), eq(10L), eq(HOLD_DURATION), eq(hold), eq(seats), any()))
                .thenThrow(new RuntimeException("order failed"));

        assertThatThrownBy(() -> startBookingUseCase.execute(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order failed");

        verify(holdManager).release(10L, "hold-key", seatIds.toList());
    }

    @Test
    void hold_해제에_실패해도_원래_예외를_유지한다() {
        final StartBookingUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());
        final RuntimeException originalException = new RuntimeException("order failed");

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(5)));
        when(bookingAvailabilityChecker.check(20L, 10L, seatIds)).thenReturn(seats);
        when(holdManager.createHold(20L, 10L, seatIds, HOLD_DURATION, FIXED_NOW)).thenReturn(hold);
        when(pendingOrderCreator.create(
                        eq(20L), eq(10L), eq(HOLD_DURATION), eq(hold), eq(seats), any()))
                .thenThrow(originalException);
        doThrow(new RuntimeException("release failed"))
                .when(holdManager)
                .release(10L, "hold-key", seatIds.toList());

        assertThatThrownBy(() -> startBookingUseCase.execute(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order failed")
                .satisfies(
                        exception -> {
                            assertThat(exception.getSuppressed()).hasSize(1);
                            assertThat(exception.getSuppressed()[0].getMessage())
                                    .isEqualTo("release failed");
                        });
    }

    @Test
    void execute는_DB_트랜잭션을_직접_시작하지_않는다() throws NoSuchMethodException {
        assertThat(
                        StartBookingUseCase.class
                                .getDeclaredMethod("execute", StartBookingUseCase.Input.class)
                                .isAnnotationPresent(Transactional.class))
                .isFalse();
    }

    /**
     * 옛 {@code CreateOrderPreparerTest}의 "prepare는 트랜잭션 없이 다른 module 공개 API를 호출한다"를 이어받는다. 준비 단계가
     * private method로 들어온 뒤에도 이 use case는 DB 트랜잭션을 열지 않는다 — 열면 다른 module 호출·분산락·Redis 왕복이 전부 그 안에
     * 들어가 connection을 오래 쥔다.
     */
    @Test
    void 준비_단계는_트랜잭션_없이_다른_module_공개_API를_호출한다() {
        assertThat(StartBookingUseCase.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (final Method method : StartBookingUseCase.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as("%s는 트랜잭션을 직접 열지 않는다", method.getName())
                    .isFalse();
        }
    }

    private void stubHold(final RequestedSeatIds seatIds) {
        lenient()
                .when(holdManager.createHold(20L, 10L, seatIds, HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold(seatIds.toList()));
    }

    private void assertError(
            final List<Long> seatIds, final Class<? extends BookingException> expected) {
        assertThatThrownBy(() -> startBookingUseCase.execute(input(seatIds)))
                .isInstanceOf(expected);
    }

    private StartBookingUseCase.Input input(final List<Long> seatIds) {
        return new StartBookingUseCase.Input(10L, seatIds, 20L, "admission-token");
    }

    private Hold hold(final List<Long> seatIds) {
        return new Hold("hold-key", 20L, 10L, seatIds, FIXED_NOW.plusMinutes(10));
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
                10L,
                new OrderAcceptanceWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(maxCanHoldCount, HOLD_DURATION),
                queueRequired
                        ? new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null)
                        : BookingEntryPolicy.none());
    }

    private PerformanceSaleSnapshot saleSnapshot() {
        return new PerformanceSaleSnapshot(
                10L, 1L, "show-title", 1L, "venue-name", FIXED_NOW.plusDays(1), Map.of(), Map.of());
    }
}
