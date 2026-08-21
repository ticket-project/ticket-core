package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.payment.model.Payment;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.domain.payment.repository.PaymentRepository;
import com.ticket.core.domain.payment.support.PaymentKeyGenerator;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class PreparePaymentUseCaseTest {

    private static final BigDecimal TOTAL_AMOUNT = BigDecimal.valueOf(120000);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentKeyGenerator paymentKeyGenerator;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-21T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 준비_요청이면_READY_결제를_생성한다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdAndStatusOrderByIdDesc(10L, PaymentStatus.READY))
                .thenReturn(Optional.empty());
        when(paymentKeyGenerator.generate()).thenReturn("PAY-1");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final PreparePaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.paymentKey()).isEqualTo("PAY-1");
        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.status()).isEqualTo(PaymentStatus.READY);
        assertThat(output.amount()).isEqualByComparingTo(TOTAL_AMOUNT);
        assertThat(output.expiresAt()).isEqualTo(FIXED_NOW.plusMinutes(5));
        verify(memberFinder).findActiveMemberById(1L);
    }

    @Test
    void 이미_READY_결제가_있으면_그것을_반환한다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        final Payment existing = new Payment("PAY-EXISTING", 10L, PaymentMethod.CARD, TOTAL_AMOUNT);
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdAndStatusOrderByIdDesc(10L, PaymentStatus.READY))
                .thenReturn(Optional.of(existing));

        final PreparePaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.paymentKey()).isEqualTo("PAY-EXISTING");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void 본인_주문이_아니면_예외를_던진다() {
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_OWNED));
    }

    @Test
    void PENDING_주문이_아니면_예외를_던진다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        order.cancel(FIXED_NOW);
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_PENDING));
    }

    @Test
    void 만료_시각을_지난_주문이면_예외를_던진다() {
        final Order order = order(FIXED_NOW.minusSeconds(1));
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_HOLD_EXPIRED));
    }

    @Test
    void 요청_금액이_주문_금액과_다르면_예외를_던진다() {
        final Order order = order(FIXED_NOW.plusMinutes(5));
        when(orderRepository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order));

        final PreparePaymentUseCase.Input wrongAmount = new PreparePaymentUseCase.Input(
                "order-key", 1L, PaymentMethod.CARD, BigDecimal.valueOf(1000)
        );

        assertThatThrownBy(() -> useCase().execute(wrongAmount))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_AMOUNT_MISMATCH));
    }

    private PreparePaymentUseCase useCase() {
        return new PreparePaymentUseCase(
                memberFinder,
                orderRepository,
                paymentRepository,
                paymentKeyGenerator,
                fixedClock
        );
    }

    private PreparePaymentUseCase.Input input() {
        return new PreparePaymentUseCase.Input("order-key", 1L, PaymentMethod.CARD, TOTAL_AMOUNT);
    }

    private Order order(final LocalDateTime expiresAt) {
        final Order order = new Order(1L, 100L, "order-key", "hold-key", TOTAL_AMOUNT, expiresAt);
        ReflectionTestUtils.setField(order, "id", 10L);
        return order;
    }
}
