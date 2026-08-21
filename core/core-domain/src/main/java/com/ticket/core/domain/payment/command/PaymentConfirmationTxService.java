package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.order.command.OrderConfirmationService;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.payment.model.Payment;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.repository.PaymentRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentConfirmationTxService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderConfirmationService orderConfirmationService;

    public record ConfirmablePayment(
            Long paymentId,
            String paymentKey,
            Long orderId,
            PaymentMethod method,
            BigDecimal amount,
            boolean alreadyApproved,
            LocalDateTime approvedAt,
            String orderKey,
            String orderStatus
    ) {}

    @Transactional(readOnly = true)
    public ConfirmablePayment loadConfirmable(
            final String paymentKey,
            final Long memberId,
            final BigDecimal amount,
            final LocalDateTime now
    ) {
        final Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        final Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.ORDER_NOT_OWNED);
        }
        if (payment.getAmount().compareTo(amount) != 0) {
            throw new CoreException(ErrorType.PAYMENT_AMOUNT_MISMATCH);
        }
        if (payment.isApproved()) {
            return toConfirmable(payment, order, true);
        }
        if (!payment.isReady()) {
            throw new CoreException(ErrorType.PAYMENT_NOT_READY);
        }
        if (!order.isPending()) {
            throw new CoreException(ErrorType.ORDER_NOT_PENDING);
        }
        if (order.isExpired(now)) {
            throw new CoreException(ErrorType.ORDER_HOLD_EXPIRED);
        }
        return toConfirmable(payment, order, false);
    }

    @Transactional
    public ConfirmPaymentUseCase.Output approve(
            final Long paymentId,
            final String pgTransactionId,
            final LocalDateTime now
    ) {
        final Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        if (payment.isApproved()) {
            final Order confirmedOrder = orderRepository.findById(payment.getOrderId())
                    .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
            return toOutput(payment, confirmedOrder);
        }
        if (!payment.isReady()) {
            throw new CoreException(ErrorType.PAYMENT_NOT_READY);
        }
        final Order order = orderRepository
                .findByIdAndStatusForUpdate(payment.getOrderId(), OrderState.PENDING)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_PENDING));
        if (order.isExpired(now)) {
            throw new CoreException(ErrorType.ORDER_HOLD_EXPIRED);
        }
        payment.approve(now, pgTransactionId);
        orderConfirmationService.confirm(order, now);
        return toOutput(payment, order);
    }

    @Transactional
    public void fail(
            final Long paymentId,
            final String failureCode,
            final String failureMessage,
            final LocalDateTime now
    ) {
        final Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));
        if (!payment.isReady()) {
            return;
        }
        payment.fail(now, failureCode, failureMessage);
    }

    private ConfirmablePayment toConfirmable(
            final Payment payment,
            final Order order,
            final boolean alreadyApproved
    ) {
        return new ConfirmablePayment(
                payment.getId(),
                payment.getPaymentKey(),
                payment.getOrderId(),
                payment.getMethod(),
                payment.getAmount(),
                alreadyApproved,
                payment.getApprovedAt(),
                order.getOrderKey(),
                order.getStatus().name()
        );
    }

    private ConfirmPaymentUseCase.Output toOutput(final Payment payment, final Order order) {
        return new ConfirmPaymentUseCase.Output(
                payment.getPaymentKey(),
                payment.getStatus(),
                payment.getApprovedAt(),
                order.getOrderKey(),
                order.getStatus().name()
        );
    }
}
