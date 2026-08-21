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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PreparePaymentUseCase {

    private final MemberFinder memberFinder;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentKeyGenerator paymentKeyGenerator;
    private final Clock clock;

    public record Input(String orderKey, Long memberId, PaymentMethod method, BigDecimal amount) {}

    public record Output(
            String paymentKey,
            String orderKey,
            BigDecimal amount,
            PaymentStatus status,
            LocalDateTime expiresAt
    ) {}

    @Transactional
    public Output execute(final Input input) {
        memberFinder.findActiveMemberById(input.memberId());
        final Order order = getPayableOrder(input);
        final Payment payment = paymentRepository
                .findFirstByOrderIdAndStatusOrderByIdDesc(order.getId(), PaymentStatus.READY)
                .orElseGet(() -> paymentRepository.save(new Payment(
                        paymentKeyGenerator.generate(),
                        order.getId(),
                        input.method(),
                        input.amount()
                )));
        return new Output(
                payment.getPaymentKey(),
                order.getOrderKey(),
                payment.getAmount(),
                payment.getStatus(),
                order.getExpiresAt()
        );
    }

    private Order getPayableOrder(final Input input) {
        final Order order = orderRepository.findByOrderKeyAndMemberIdForUpdate(input.orderKey(), input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_OWNED));
        if (!order.isPending()) {
            throw new CoreException(ErrorType.ORDER_NOT_PENDING);
        }
        if (order.isExpired(LocalDateTime.now(clock))) {
            throw new CoreException(ErrorType.ORDER_HOLD_EXPIRED);
        }
        if (order.getTotalAmount().compareTo(input.amount()) != 0) {
            throw new CoreException(ErrorType.PAYMENT_AMOUNT_MISMATCH);
        }
        return order;
    }
}
