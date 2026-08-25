package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.command.HoldSeatAvailabilityValidator;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.queue.AdmissionGuard;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CreateOrderValidatorTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private PerformanceBookingPolicyFinder performanceBookingPolicyFinder;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HoldSeatAvailabilityValidator holdSeatAvailabilityValidator;

    @Mock
    private AdmissionGuard admissionGuard;

    @InjectMocks
    private CreateOrderValidator validator;

    @Test
    void 검증은_하나의_읽기_전용_트랜잭션에서_수행한다() throws NoSuchMethodException {
        Transactional transactional = CreateOrderValidator.class
                .getDeclaredMethod(
                        "validate",
                        CreateOrderUseCase.Input.class,
                        RequestedSeatIds.class,
                        LocalDateTime.class
                )
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void 예매가_마감된_회차는_DB_검증으로_넘어가지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceBookingPolicyFinder.findById(10L))
                .thenReturn(policy(3, FIXED_NOW.minusHours(2), FIXED_NOW.minusHours(1), null));

        assertError(seatIds, ErrorType.PERFORMANCE_IS_PAST);

        verifyNoInteractions(memberFinder, orderRepository, holdSeatAvailabilityValidator, admissionGuard);
    }

    @Test
    void 최대_선점_가능_수량을_초과하면_DB_검증으로_넘어가지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L));
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(openPolicy(2));

        assertError(seatIds, ErrorType.EXCEED_HOLD_LIMIT);

        verifyNoInteractions(memberFinder, orderRepository, holdSeatAvailabilityValidator, admissionGuard);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(openPolicy(3));
        when(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(20L, 10L, OrderState.PENDING)).thenReturn(false);
        when(holdSeatAvailabilityValidator.validate(10L, seatIds)).thenReturn(List.of());

        validator.validate(input(seatIds), seatIds, FIXED_NOW);

        verify(admissionGuard, never()).ensureAdmitted(10L, 20L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_입장_검사를_DB_검증보다_먼저_한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceBookingPolicyFinder.findById(10L))
                .thenReturn(policy(3, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), QueueMode.FORCE_ON));
        doThrowAdmissionRequired();

        assertError(seatIds, ErrorType.ADMISSION_TOKEN_REQUIRED);

        verifyNoInteractions(memberFinder, orderRepository, holdSeatAvailabilityValidator);
    }

    @Test
    void 진행중인_pending_주문이_있으면_좌석을_조회하지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(openPolicy(3));
        when(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(20L, 10L, OrderState.PENDING)).thenReturn(true);

        assertError(seatIds, ErrorType.PENDING_ORDER_ALREADY_EXISTS);

        verifyNoInteractions(holdSeatAvailabilityValidator);
    }

    @Test
    void 유효한_요청이면_정책과_좌석을_함께_반환한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        PerformanceBookingPolicyView policy = openPolicy(3);
        List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));

        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(policy);
        when(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(20L, 10L, OrderState.PENDING)).thenReturn(false);
        when(holdSeatAvailabilityValidator.validate(10L, seatIds)).thenReturn(seats);

        ValidatedOrderRequest result = validator.validate(input(seatIds), seatIds, FIXED_NOW);

        assertThat(result.policy()).isSameAs(policy);
        assertThat(result.performanceSeats()).isSameAs(seats);
        verify(memberFinder).ensureActiveMemberExists(20L);
    }

    @Test
    void 좌석_수_한도가_없으면_요청_수량을_제한하지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L, 4L, 5L));
        PerformanceBookingPolicyView policy = openPolicy(null);

        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(policy);
        when(orderRepository.existsByMemberIdAndPerformanceIdAndStatus(20L, 10L, OrderState.PENDING)).thenReturn(false);
        when(holdSeatAvailabilityValidator.validate(10L, seatIds)).thenReturn(List.of());

        ValidatedOrderRequest result = validator.validate(input(seatIds), seatIds, FIXED_NOW);

        assertThat(result.policy()).isSameAs(policy);
    }

    private void doThrowAdmissionRequired() {
        org.mockito.Mockito.doThrow(new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED))
                .when(admissionGuard).ensureAdmitted(10L, 20L, "admission-token");
    }

    private void assertError(final RequestedSeatIds seatIds, final ErrorType errorType) {
        assertThatThrownBy(() -> validator.validate(input(seatIds), seatIds, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(errorType));
    }

    private CreateOrderUseCase.Input input(final RequestedSeatIds seatIds) {
        return new CreateOrderUseCase.Input(10L, seatIds.toList(), 20L, "admission-token");
    }

    private PerformanceBookingPolicyView openPolicy(final Integer maxCanHoldCount) {
        return policy(maxCanHoldCount, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), null);
    }

    private PerformanceBookingPolicyView policy(
            final Integer maxCanHoldCount,
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final QueueMode queueMode
    ) {
        return new PerformanceBookingPolicyView(
                10L,
                orderOpenTime,
                orderCloseTime,
                maxCanHoldCount,
                300,
                queueMode,
                null,
                null,
                null,
                null
        );
    }
}
