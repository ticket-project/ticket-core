package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @InjectMocks
    private CreateOrderValidator checker;

    @Test
    void 최대_선점_가능_수량을_초과하면_예외를_던진다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L));
        PerformanceBookingPolicyView performance = createPerformance(2, 300);

        when(performanceBookingPolicyFinder.findValidById(10L, FIXED_NOW)).thenReturn(performance);

        assertThatThrownBy(() -> checker.validate(20L, 10L, seatIds, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ErrorType.EXCEED_HOLD_LIMIT));

        verify(memberFinder).findActiveMemberById(20L);
        verify(performanceBookingPolicyFinder).findValidById(10L, FIXED_NOW);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void 진행중인_pending_주문이_있으면_예외를_던진다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        PerformanceBookingPolicyView performance = createPerformance(3, 300);

        when(performanceBookingPolicyFinder.findValidById(10L, FIXED_NOW)).thenReturn(performance);
        when(orderRepository.findByMemberIdAndPerformanceIdAndStatus(20L, 10L, OrderState.PENDING)).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(com.ticket.core.domain.order.model.Order.class)));

        assertThatThrownBy(() -> checker.validate(20L, 10L, seatIds, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ErrorType.PENDING_ORDER_ALREADY_EXISTS));
    }

    @Test
    void 유효한_요청이면_공연을_반환한다() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L));
        PerformanceBookingPolicyView performance = createPerformance(3, 300);

        when(performanceBookingPolicyFinder.findValidById(10L, FIXED_NOW)).thenReturn(performance);
        when(orderRepository.findByMemberIdAndPerformanceIdAndStatus(20L, 10L, OrderState.PENDING)).thenReturn(java.util.Optional.empty());

        PerformanceBookingPolicyView result = checker.validate(20L, 10L, seatIds, FIXED_NOW);

        assertThat(result).isSameAs(performance);
    }

    @Test
    void does_not_limit_requested_seat_count_when_hold_limit_is_null() {
        RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(1L, 2L, 3L, 4L, 5L));
        PerformanceBookingPolicyView performance = createPerformance(null, 300);

        when(performanceBookingPolicyFinder.findValidById(10L, FIXED_NOW)).thenReturn(performance);
        when(orderRepository.findByMemberIdAndPerformanceIdAndStatus(20L, 10L, OrderState.PENDING)).thenReturn(java.util.Optional.empty());

        PerformanceBookingPolicyView result = checker.validate(20L, 10L, seatIds, FIXED_NOW);

        assertThat(result).isSameAs(performance);
    }

    private PerformanceBookingPolicyView createPerformance(final Integer maxCanHoldCount, final int holdTimeSeconds) {
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 10, 0);
        return new PerformanceBookingPolicyView(
                10L,
                now.minusHours(1),
                now.plusHours(3),
                maxCanHoldCount,
                holdTimeSeconds,
                null,
                null,
                null,
                null,
                null
        );
    }
}
