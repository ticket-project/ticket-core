package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.gateway.PaymentGatewayClient;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConfirmPaymentUseCase {

    private final MemberFinder memberFinder;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentConfirmationTxService txService;
    private final Clock clock;

    public record Input(String paymentKey, Long memberId, BigDecimal amount) {}

    public record Output(
            String paymentKey,
            PaymentStatus status,
            LocalDateTime approvedAt,
            String orderKey,
            String orderStatus
    ) {}

    public Output execute(final Input input) {
        memberFinder.findActiveMemberById(input.memberId());
        final LocalDateTime now = LocalDateTime.now(clock);
        final PaymentConfirmationTxService.ConfirmablePayment confirmable =
                txService.loadConfirmable(input.paymentKey(), input.memberId(), input.amount(), now);

        if (confirmable.alreadyApproved()) {
            return new Output(
                    confirmable.paymentKey(),
                    PaymentStatus.APPROVED,
                    confirmable.approvedAt(),
                    confirmable.orderKey(),
                    confirmable.orderStatus()
            );
        }

        final PaymentApprovalResult result = paymentGatewayClient.approve(new PaymentApprovalCommand(
                confirmable.paymentKey(),
                confirmable.orderId(),
                confirmable.amount(),
                confirmable.method()
        ));

        if (!result.approved()) {
            txService.fail(confirmable.paymentId(), result.failureCode(), result.failureMessage(), now);
            throw new CoreException(ErrorType.PAYMENT_DECLINED, result.failureMessage());
        }

        return txService.approve(confirmable.paymentId(), result.pgTransactionId(), now);
    }
}
