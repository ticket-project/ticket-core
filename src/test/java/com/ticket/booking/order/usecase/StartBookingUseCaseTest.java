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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.admission.AdmissionGuard;
import com.ticket.booking.admission.AdmissionVerifier;
import com.ticket.booking.concurrency.RecordingLockManager;
import com.ticket.booking.exception.AdmissionTokenRequiredException;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.exception.HoldLimitExceededException;
import com.ticket.booking.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.salespolicy.domain.BookingEntryPolicy;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.OrderAcceptanceWindow;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.api.PerformanceSaleCatalogApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

/**
 * 예매 시작 workflow 전체를 고정한다.
 *
 * <p>준비 단계(판매 정책 확인 → 입장 검사 → 회원 확인 → 좌석 확인 → 표시값 조회)는 이 use case의 private method다. 그래서 "어떤 조건에서 어떤
 * {@code BookingException}이 나오고, 그때 뒤 단계로 넘어가지 않는가"를 여기서 관찰 가능한 호출로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class StartBookingUseCaseTest {
    private static final Duration HOLD_DURATION = Duration.ofSeconds(600);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);
    private static final long PERFORMANCE_ID = 10L;
    private static final long MEMBER_ID = 20L;
    private static final String ADMISSION_TOKEN = "admission-token";
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
                // 실제 collaborator를 mock repository/verifier 위에 씌운다 -- 그래야 "없으면 404"와
                // "대기열이 필요할 때만 검증"이라는 분기가 mock에 가려지지 않고 그대로 검증된다.
                new StartBookingUseCase(
                        lockManager,
                        new PerformanceSaleFinder(performanceSalesPolicyRepository),
                        new AdmissionGuard(admissionVerifier),
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
                new StartBookingUseCase.Input(
                        PERFORMANCE_ID, List.of(3L, 1L, 3L), MEMBER_ID, ADMISSION_TOKEN);

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
                new StartBookingUseCase.Input(
                        PERFORMANCE_ID, List.of(), MEMBER_ID, ADMISSION_TOKEN);

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

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(5)));
        when(bookingAvailabilityChecker.check(MEMBER_ID, PERFORMANCE_ID, seatIds))
                .thenReturn(seats);
        when(performanceSaleCatalog.getSaleSnapshot(PERFORMANCE_ID, Set.copyOf(seatIds.toList())))
                .thenReturn(saleSnapshot);
        when(holdManager.createHold(
                        MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);
        when(pendingOrderCreator.create(
                        MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, hold, seats, saleSnapshot))
                .thenReturn("order-key");

        final StartBookingUseCase.Output output = startBookingUseCase.execute(input);

        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.status()).isEqualTo(OrderState.PENDING);
        assertThat(output.expiresAt()).isEqualTo(hold.expiresAt());
        assertThat(output.remainingSeconds()).isEqualTo(600L);

        final InOrder inOrder =
                inOrder(
                        performanceSalesPolicyRepository,
                        memberLookup,
                        bookingAvailabilityChecker,
                        performanceSaleCatalog,
                        holdManager,
                        pendingOrderCreator);
        inOrder.verify(performanceSalesPolicyRepository).findById(PERFORMANCE_ID);
        inOrder.verify(memberLookup).requireActive(MEMBER_ID);
        inOrder.verify(bookingAvailabilityChecker).check(MEMBER_ID, PERFORMANCE_ID, seatIds);
        inOrder.verify(performanceSaleCatalog)
                .getSaleSnapshot(PERFORMANCE_ID, Set.copyOf(seatIds.toList()));
        inOrder.verify(holdManager)
                .createHold(MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW);
        inOrder.verify(pendingOrderCreator)
                .create(MEMBER_ID, PERFORMANCE_ID, HOLD_DURATION, hold, seats, saleSnapshot);
    }

    /** 확인한 정책과 좌석은 반환되지 않고 뒤 단계의 인자로만 관찰된다. */
    @Test
    void 확인한_정책의_선점_시간과_좌석을_그대로_뒤_단계로_넘긴다() {
        final StartBookingUseCase.Input input = input(List.of(1L, 2L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats =
                List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(3)));
        when(bookingAvailabilityChecker.check(MEMBER_ID, PERFORMANCE_ID, seatIds))
                .thenReturn(seats);
        when(holdManager.createHold(
                        MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);

        startBookingUseCase.execute(input);

        verify(memberLookup).requireActive(MEMBER_ID);
        verify(pendingOrderCreator)
                .create(
                        eq(MEMBER_ID),
                        eq(PERFORMANCE_ID),
                        eq(HOLD_DURATION),
                        eq(hold),
                        same(seats),
                        any());
    }

    @Test
    void 예매가_마감된_회차는_DB_검증으로_넘어가지_않는다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
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
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(2)));

        assertError(seatIds, HoldLimitExceededException.class);

        verifyNoInteractions(memberLookup, admissionVerifier, bookingAvailabilityChecker);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        final StartBookingUseCase.Input input = input(List.of(1L, 2L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(3)));
        when(bookingAvailabilityChecker.check(MEMBER_ID, PERFORMANCE_ID, seatIds))
                .thenReturn(List.of());
        stubHold(seatIds);

        startBookingUseCase.execute(input);

        verify(admissionVerifier, never()).verify(PERFORMANCE_ID, MEMBER_ID, ADMISSION_TOKEN);
    }

    @Test
    void 대기열이_필요한_회차는_입장_검사를_DB_검증보다_먼저_한다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(
                        Optional.of(
                                policy(3, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), true)));
        doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier)
                .verify(PERFORMANCE_ID, MEMBER_ID, ADMISSION_TOKEN);

        assertThatThrownBy(() -> startBookingUseCase.execute(input(seatIds)))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(memberLookup, bookingAvailabilityChecker);
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_전파한다() {
        final List<Long> seatIds = List.of(1L, 2L);
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(3)));
        when(bookingAvailabilityChecker.check(
                        MEMBER_ID, PERFORMANCE_ID, RequestedSeatIds.from(seatIds)))
                .thenThrow(new PendingOrderAlreadyExistsException(MEMBER_ID, PERFORMANCE_ID));

        assertError(seatIds, PendingOrderAlreadyExistsException.class);

        verifyNoInteractions(holdManager, pendingOrderCreator);
    }

    @Test
    void 좌석_수_한도가_없으면_요청_수량을_제한하지_않는다() {
        final StartBookingUseCase.Input input = input(List.of(1L, 2L, 3L, 4L, 5L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(null)));
        when(bookingAvailabilityChecker.check(MEMBER_ID, PERFORMANCE_ID, seatIds))
                .thenReturn(List.of());
        stubHold(seatIds);

        startBookingUseCase.execute(input);

        verify(bookingAvailabilityChecker).check(MEMBER_ID, PERFORMANCE_ID, seatIds);
    }

    @Test
    void 주문_저장_트랜잭션이_실패하면_hold를_해제한다() {
        final StartBookingUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(5)));
        when(bookingAvailabilityChecker.check(MEMBER_ID, PERFORMANCE_ID, seatIds))
                .thenReturn(seats);
        when(holdManager.createHold(
                        MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);
        when(pendingOrderCreator.create(
                        eq(MEMBER_ID),
                        eq(PERFORMANCE_ID),
                        eq(HOLD_DURATION),
                        eq(hold),
                        eq(seats),
                        any()))
                .thenThrow(new RuntimeException("order failed"));

        assertThatThrownBy(() -> startBookingUseCase.execute(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("order failed");

        verify(holdManager).release(PERFORMANCE_ID, "hold-key", seatIds.toList());
    }

    @Test
    void hold_해제에_실패해도_원래_예외를_유지한다() {
        final StartBookingUseCase.Input input = input(List.of(7L, 3L));
        final RequestedSeatIds seatIds = RequestedSeatIds.from(input.seatIds());
        final List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class));
        final Hold hold = hold(seatIds.toList());
        final RuntimeException originalException = new RuntimeException("order failed");

        when(performanceSalesPolicyRepository.findById(PERFORMANCE_ID))
                .thenReturn(Optional.of(openPolicy(5)));
        when(bookingAvailabilityChecker.check(MEMBER_ID, PERFORMANCE_ID, seatIds))
                .thenReturn(seats);
        when(holdManager.createHold(
                        MEMBER_ID, PERFORMANCE_ID, seatIds.toList(), HOLD_DURATION, FIXED_NOW))
                .thenReturn(hold);
        when(pendingOrderCreator.create(
                        eq(MEMBER_ID),
                        eq(PERFORMANCE_ID),
                        eq(HOLD_DURATION),
                        eq(hold),
                        eq(seats),
                        any()))
                .thenThrow(originalException);
        doThrow(new RuntimeException("release failed"))
                .when(holdManager)
                .release(PERFORMANCE_ID, "hold-key", seatIds.toList());

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
     * 준비 단계가 private method여도 이 use case는 DB 트랜잭션을 열지 않는다 — 열면 다른 module 호출·분산락·Redis 왕복이 전부 그 안에
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
                .when(
                        holdManager.createHold(
                                MEMBER_ID,
                                PERFORMANCE_ID,
                                seatIds.toList(),
                                HOLD_DURATION,
                                FIXED_NOW))
                .thenReturn(hold(seatIds.toList()));
    }

    private void assertError(
            final List<Long> seatIds, final Class<? extends BookingException> expected) {
        assertThatThrownBy(() -> startBookingUseCase.execute(input(seatIds)))
                .isInstanceOf(expected);
    }

    private StartBookingUseCase.Input input(final List<Long> seatIds) {
        return new StartBookingUseCase.Input(PERFORMANCE_ID, seatIds, MEMBER_ID, ADMISSION_TOKEN);
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
                new OrderAcceptanceWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(maxCanHoldCount, HOLD_DURATION),
                queueRequired
                        ? new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null)
                        : BookingEntryPolicy.none());
    }

    private PerformanceSaleSnapshot saleSnapshot() {
        return new PerformanceSaleSnapshot(
                PERFORMANCE_ID,
                1L,
                "show-title",
                1L,
                "venue-name",
                FIXED_NOW.plusDays(1),
                Map.of(),
                Map.of());
    }
}
