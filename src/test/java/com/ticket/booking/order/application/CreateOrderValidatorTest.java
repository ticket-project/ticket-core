package com.ticket.booking.order.application;

import com.ticket.booking.order.application.usecase.CreateOrderUseCase;

import com.ticket.booking.admission.exception.AdmissionTokenRequiredException;
import com.ticket.booking.support.exception.BookingException;
import com.ticket.booking.salespolicy.exception.ExceedHoldLimitException;
import com.ticket.booking.order.exception.PendingOrderAlreadyExistsException;
import com.ticket.booking.support.domain.RequestedSeatIds;
import com.ticket.booking.salespolicy.domain.BookingEntryPolicy;
import com.ticket.booking.salespolicy.domain.HoldPolicy;
import com.ticket.booking.salespolicy.domain.OrderAcceptanceWindow;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.QueueMode;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.admission.application.AdmissionVerifier;
import com.ticket.booking.support.exception.PerformanceIsPastException;
import com.ticket.show.PerformanceSaleCatalog;
import com.ticket.show.PerformanceSaleSnapshot;
import com.ticket.member.MemberLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private MemberLookup memberLookup;

    @Mock
    private PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    @Mock
    private PerformanceSaleCatalog performanceSaleCatalog;

    @Mock
    private AdmissionVerifier admissionVerifier;

    @Mock
    private PendingOrderLocalValidator pendingOrderLocalValidator;

    @InjectMocks
    private CreateOrderValidator validator;

    @Test
    void booking_local_읽기는_별도_component의_읽기_전용_트랜잭션에서_수행한다() throws NoSuchMethodException {
        Transactional transactional = PendingOrderLocalValidator.class
                .getDeclaredMethod("validate", Long.class, Long.class, RequestedSeatIds.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void validate는_트랜잭션_없이_다른_module_공개_API를_호출한다() throws NoSuchMethodException {
        Transactional transactional = CreateOrderValidator.class
                .getDeclaredMethod(
                        "validate",
                        CreateOrderUseCase.Input.class,
                        RequestedSeatIds.class,
                        LocalDateTime.class
                )
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    @Test
    void 예매가_마감된_회차는_DB_검증으로_넘어가지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(3, FIXED_NOW.minusHours(2), FIXED_NOW.minusHours(1), false)));

        assertError(seatIds, PerformanceIsPastException.class);

        verifyNoInteractions(orderRepositoryCollaborators());
    }

    @Test
    void 최대_선점_가능_수량을_초과하면_DB_검증으로_넘어가지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L));
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(2)));

        assertError(seatIds, ExceedHoldLimitException.class);

        verifyNoInteractions(memberLookup, admissionVerifier, pendingOrderLocalValidator);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(3)));
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds)).thenReturn(List.of());

        validator.validate(input(seatIds), seatIds, FIXED_NOW);

        verify(admissionVerifier, never()).verify(10L, 20L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_입장_검사를_DB_검증보다_먼저_한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceSalesPolicyRepository.findById(10L))
                .thenReturn(Optional.of(policy(3, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), true)));
        doThrowAdmissionRequired();

        assertThatThrownBy(() -> validator.validate(input(seatIds), seatIds, FIXED_NOW))
                .isInstanceOf(AdmissionTokenRequiredException.class);

        verifyNoInteractions(memberLookup, pendingOrderLocalValidator);
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_전파한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(openPolicy(3)));
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds))
                .thenThrow(new PendingOrderAlreadyExistsException(20L, 10L));

        assertError(seatIds, PendingOrderAlreadyExistsException.class);
    }

    @Test
    void 유효한_요청이면_정책과_좌석을_함께_반환한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        PerformanceSalesPolicy policy = openPolicy(3);
        List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds)).thenReturn(seats);

        ValidatedOrderRequest result = validator.validate(input(seatIds), seatIds, FIXED_NOW);

        assertThat(result.policy()).isSameAs(policy);
        assertThat(result.performanceSeats()).isSameAs(seats);
        verify(memberLookup).requireActive(20L);
    }

    @Test
    void 좌석_수_한도가_없으면_요청_수량을_제한하지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L, 4L, 5L));
        PerformanceSalesPolicy policy = openPolicy(null);

        when(performanceSalesPolicyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds)).thenReturn(List.of());

        ValidatedOrderRequest result = validator.validate(input(seatIds), seatIds, FIXED_NOW);

        assertThat(result.policy()).isSameAs(policy);
    }

    private Object[] orderRepositoryCollaborators() {
        return new Object[] {memberLookup, admissionVerifier, pendingOrderLocalValidator};
    }

    private void doThrowAdmissionRequired() {
        org.mockito.Mockito.doThrow(new AdmissionTokenRequiredException())
                .when(admissionVerifier).verify(10L, 20L, "admission-token");
    }

    private void assertError(final RequestedSeatIds seatIds, final Class<? extends BookingException> expected) {
        assertThatThrownBy(() -> validator.validate(input(seatIds), seatIds, FIXED_NOW))
                .isInstanceOf(expected);
    }

    private CreateOrderUseCase.Input input(final RequestedSeatIds seatIds) {
        return new CreateOrderUseCase.Input(10L, seatIds.toList(), 20L, "admission-token");
    }

    private PerformanceSalesPolicy openPolicy(final Integer maxCanHoldCount) {
        return policy(maxCanHoldCount, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), false);
    }

    private PerformanceSalesPolicy policy(
            final Integer maxCanHoldCount,
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final boolean queueRequired
    ) {
        return new PerformanceSalesPolicy(
                10L,
                new OrderAcceptanceWindow(orderOpenTime, orderCloseTime),
                new HoldPolicy(maxCanHoldCount, Duration.ofSeconds(300)),
                queueRequired
                        ? new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null)
                        : BookingEntryPolicy.none()
        );
    }
}
