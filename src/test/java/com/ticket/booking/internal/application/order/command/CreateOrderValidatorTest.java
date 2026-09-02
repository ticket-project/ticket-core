package com.ticket.booking.internal.application.order.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.domain.order.command.create.ValidatedOrderRequest;
import com.ticket.booking.internal.domain.order.command.create.RequestedSeatIds;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.identity.MemberLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private BookingPolicyLookup bookingPolicyLookup;

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
        when(bookingPolicyLookup.getBookingPolicy(10L, seatIds.toList()))
                .thenReturn(policy(3, FIXED_NOW.minusHours(2), FIXED_NOW.minusHours(1), false));

        assertError(seatIds, ErrorType.PERFORMANCE_IS_PAST);

        verifyNoInteractions(orderRepositoryCollaborators());
    }

    @Test
    void 최대_선점_가능_수량을_초과하면_DB_검증으로_넘어가지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L));
        when(bookingPolicyLookup.getBookingPolicy(10L, seatIds.toList())).thenReturn(openPolicy(2));

        assertError(seatIds, ErrorType.EXCEED_HOLD_LIMIT);

        verifyNoInteractions(memberLookup, admissionVerifier, pendingOrderLocalValidator);
    }

    @Test
    void 대기열이_필요없는_회차는_입장_검사를_하지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(bookingPolicyLookup.getBookingPolicy(10L, seatIds.toList())).thenReturn(openPolicy(3));
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds)).thenReturn(List.of());

        validator.validate(input(seatIds), seatIds, FIXED_NOW);

        verify(admissionVerifier, never()).verify(10L, 20L, "admission-token");
    }

    @Test
    void 대기열이_필요한_회차는_입장_검사를_DB_검증보다_먼저_한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(bookingPolicyLookup.getBookingPolicy(10L, seatIds.toList()))
                .thenReturn(policy(3, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), true));
        doThrowAdmissionRequired();

        assertError(seatIds, ErrorType.ADMISSION_TOKEN_REQUIRED);

        verifyNoInteractions(memberLookup, pendingOrderLocalValidator);
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_전파한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        when(bookingPolicyLookup.getBookingPolicy(10L, seatIds.toList())).thenReturn(openPolicy(3));
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds))
                .thenThrow(new CoreException(ErrorType.PENDING_ORDER_ALREADY_EXISTS));

        assertError(seatIds, ErrorType.PENDING_ORDER_ALREADY_EXISTS);
    }

    @Test
    void 유효한_요청이면_정책과_좌석을_함께_반환한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        BookingPolicySnapshot policy = openPolicy(3);
        List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class), mock(PerformanceSeat.class));

        when(bookingPolicyLookup.getBookingPolicy(10L, seatIds.toList())).thenReturn(policy);
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds)).thenReturn(seats);

        ValidatedOrderRequest result = validator.validate(input(seatIds), seatIds, FIXED_NOW);

        assertThat(result.policy()).isSameAs(policy);
        assertThat(result.performanceSeats()).isSameAs(seats);
        verify(memberLookup).requireActive(20L);
    }

    @Test
    void 좌석_수_한도가_없으면_요청_수량을_제한하지_않는다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L, 4L, 5L));
        BookingPolicySnapshot policy = openPolicy(null);

        when(bookingPolicyLookup.getBookingPolicy(10L, seatIds.toList())).thenReturn(policy);
        when(pendingOrderLocalValidator.validate(20L, 10L, seatIds)).thenReturn(List.of());

        ValidatedOrderRequest result = validator.validate(input(seatIds), seatIds, FIXED_NOW);

        assertThat(result.policy()).isSameAs(policy);
    }

    private Object[] orderRepositoryCollaborators() {
        return new Object[] {memberLookup, admissionVerifier, pendingOrderLocalValidator};
    }

    private void doThrowAdmissionRequired() {
        org.mockito.Mockito.doThrow(new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED))
                .when(admissionVerifier).verify(10L, 20L, "admission-token");
    }

    private void assertError(final RequestedSeatIds seatIds, final ErrorType errorType) {
        assertThatThrownBy(() -> validator.validate(input(seatIds), seatIds, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(errorType));
    }

    private CreateOrderUseCase.Input input(final RequestedSeatIds seatIds) {
        return new CreateOrderUseCase.Input(10L, seatIds.toList(), 20L, "admission-token");
    }

    private BookingPolicySnapshot openPolicy(final Integer maxCanHoldCount) {
        return policy(maxCanHoldCount, FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(3), false);
    }

    private BookingPolicySnapshot policy(
            final Integer maxCanHoldCount,
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final boolean queueRequired
    ) {
        return new BookingPolicySnapshot(
                10L,
                true,
                orderOpenTime,
                orderCloseTime,
                maxCanHoldCount,
                300,
                null,
                null,
                null,
                queueRequired,
                Map.of()
        );
    }
}
