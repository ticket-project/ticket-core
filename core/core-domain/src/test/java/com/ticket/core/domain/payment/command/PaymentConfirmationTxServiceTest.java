package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.order.command.OrderConfirmationService;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.payment.model.Payment;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.domain.payment.repository.PaymentRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class PaymentConfirmationTxServiceTest {

    private static final Long PAYMENT_ID = 20L;
    private static final Long ORDER_ID = 10L;
    private static final Long MEMBER_ID = 1L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(120000);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderConfirmationService orderConfirmationService;

    // ---- loadConfirmable ----

    @Test
    void loadConfirmable_결제를_못_찾으면_예외를_던진다() {
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().loadConfirmable("PAY-1", MEMBER_ID, AMOUNT, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
    }

    @Test
    void loadConfirmable_결제는_있지만_주문을_못_찾으면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.READY);
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().loadConfirmable("PAY-1", MEMBER_ID, AMOUNT, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
    }

    @Test
    void loadConfirmable_주문_소유자가_다르면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.READY);
        final Order order = order(OrderState.PENDING, FIXED_NOW.plusMinutes(5), MEMBER_ID + 1);
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().loadConfirmable("PAY-1", MEMBER_ID, AMOUNT, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_OWNED));
    }

    @Test
    void loadConfirmable_요청_금액이_결제_금액과_다르면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.READY);
        final Order order = order(OrderState.PENDING, FIXED_NOW.plusMinutes(5), MEMBER_ID);
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service()
                .loadConfirmable("PAY-1", MEMBER_ID, BigDecimal.valueOf(1000), FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_AMOUNT_MISMATCH));
    }

    @Test
    void loadConfirmable_이미_APPROVED인_결제면_이후_검증_없이_그대로_반환한다() {
        final Payment payment = payment(PaymentStatus.APPROVED);
        // 주문이 이미 CONFIRMED여도(더는 PENDING이 아니어도) 통과해야 한다
        final Order order = order(OrderState.CONFIRMED, FIXED_NOW.minusMinutes(30), MEMBER_ID);
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        final PaymentConfirmationTxService.ConfirmablePayment confirmable =
                service().loadConfirmable("PAY-1", MEMBER_ID, AMOUNT, FIXED_NOW);

        assertThat(confirmable.alreadyApproved()).isTrue();
        assertThat(confirmable.approvedAt()).isEqualTo(payment.getApprovedAt());
    }

    @Test
    void loadConfirmable_FAILED_결제면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.FAILED);
        final Order order = order(OrderState.PENDING, FIXED_NOW.plusMinutes(5), MEMBER_ID);
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().loadConfirmable("PAY-1", MEMBER_ID, AMOUNT, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_NOT_READY));
    }

    @Test
    void loadConfirmable_주문이_PENDING이_아니면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.READY);
        final Order order = order(OrderState.CANCELED, FIXED_NOW.plusMinutes(5), MEMBER_ID);
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().loadConfirmable("PAY-1", MEMBER_ID, AMOUNT, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_PENDING));
    }

    @Test
    void loadConfirmable_주문이_만료_시각을_지났으면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.READY);
        final Order order = order(OrderState.PENDING, FIXED_NOW.minusSeconds(1), MEMBER_ID);
        when(paymentRepository.findByPaymentKey("PAY-1")).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().loadConfirmable("PAY-1", MEMBER_ID, AMOUNT, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_HOLD_EXPIRED));
    }

    // ---- approve ----

    @Test
    void approve_정상_경로면_결제와_주문이_확정되고_결과를_반환한다() {
        final Payment payment = payment(PaymentStatus.READY);
        final Order order = order(OrderState.PENDING, FIXED_NOW.plusMinutes(5), MEMBER_ID);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdAndStatusForUpdate(ORDER_ID, OrderState.PENDING))
                .thenReturn(Optional.of(order));

        final ConfirmPaymentUseCase.Output output = service().approve(PAYMENT_ID, "PG-TX-1", FIXED_NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(FIXED_NOW);
        verify(orderConfirmationService).confirm(order, FIXED_NOW);
        assertThat(output.paymentKey()).isEqualTo("PAY-1");
        assertThat(output.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(output.approvedAt()).isEqualTo(FIXED_NOW);
        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.orderStatus()).isEqualTo(order.getStatus().name());
    }

    @Test
    void approve_이미_APPROVED인_결제면_확정_없이_같은_결과를_반환한다() {
        final Payment payment = payment(PaymentStatus.APPROVED);
        final Order order = order(OrderState.CONFIRMED, FIXED_NOW.minusMinutes(30), MEMBER_ID);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        final ConfirmPaymentUseCase.Output output = service().approve(PAYMENT_ID, "PG-TX-1", FIXED_NOW);

        verifyNoInteractions(orderConfirmationService);
        assertThat(output.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(output.paymentKey()).isEqualTo("PAY-1");
    }

    @Test
    void approve_READY도_APPROVED도_아니면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.FAILED);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service().approve(PAYMENT_ID, "PG-TX-1", FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_NOT_READY));
        verifyNoInteractions(orderConfirmationService);
    }

    @Test
    void approve_주문이_PENDING_상태로_없으면_예외를_던진다() {
        final Payment payment = payment(PaymentStatus.READY);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdAndStatusForUpdate(ORDER_ID, OrderState.PENDING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().approve(PAYMENT_ID, "PG-TX-1", FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_PENDING));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(orderConfirmationService);
    }

    @Test
    void approve_주문이_만료됐으면_예외를_던지고_확정하지_않는다() {
        final Payment payment = payment(PaymentStatus.READY);
        final Order order = order(OrderState.PENDING, FIXED_NOW.minusSeconds(1), MEMBER_ID);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdAndStatusForUpdate(ORDER_ID, OrderState.PENDING))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service().approve(PAYMENT_ID, "PG-TX-1", FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ORDER_HOLD_EXPIRED));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(orderConfirmationService);
    }

    // ---- fail ----

    @Test
    void fail_READY_결제면_FAILED로_전이하고_실패정보를_기록한다() {
        final Payment payment = payment(PaymentStatus.READY);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service().fail(PAYMENT_ID, "DECLINED", "거절되었습니다.", FIXED_NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("DECLINED");
        assertThat(payment.getFailureMessage()).isEqualTo("거절되었습니다.");
        assertThat(payment.getFailedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void fail_이미_APPROVED인_결제면_아무것도_바꾸지_않는다() {
        final Payment payment = payment(PaymentStatus.APPROVED);
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service().fail(PAYMENT_ID, "DECLINED", "거절되었습니다.", FIXED_NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getFailureCode()).isNull();
        assertThat(payment.getFailureMessage()).isNull();
    }

    private PaymentConfirmationTxService service() {
        return new PaymentConfirmationTxService(paymentRepository, orderRepository, orderConfirmationService);
    }

    private Payment payment(final PaymentStatus status) {
        final Payment payment = new Payment("PAY-1", ORDER_ID, PaymentMethod.CARD, AMOUNT);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        if (status == PaymentStatus.APPROVED) {
            payment.approve(FIXED_NOW.minusMinutes(1), "PG-TX-EXISTING");
        } else if (status == PaymentStatus.FAILED) {
            payment.fail(FIXED_NOW.minusMinutes(1), "EXISTING_FAILURE", "이전 실패");
        }
        return payment;
    }

    private Order order(final OrderState status, final LocalDateTime expiresAt, final Long memberId) {
        final Order order = new Order(memberId, 100L, "order-key", "hold-key", AMOUNT, expiresAt);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        if (status == OrderState.CONFIRMED) {
            order.confirm(FIXED_NOW.minusMinutes(1));
        } else if (status == OrderState.CANCELED) {
            order.cancel(FIXED_NOW.minusMinutes(1));
        }
        return order;
    }
}
