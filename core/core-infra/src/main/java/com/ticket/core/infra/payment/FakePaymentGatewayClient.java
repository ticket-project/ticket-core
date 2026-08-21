package com.ticket.core.infra.payment;

import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.gateway.PaymentGatewayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FakePaymentGatewayClient implements PaymentGatewayClient {

    private final boolean approveAll;

    public FakePaymentGatewayClient(
            @Value("${ticket.payment.fake.approve:true}") final boolean approveAll
    ) {
        this.approveAll = approveAll;
    }

    @Override
    public PaymentApprovalResult approve(final PaymentApprovalCommand command) {
        if (!approveAll) {
            return PaymentApprovalResult.declined("FAKE_DECLINED", "가짜 게이트웨이가 승인을 거절했습니다.");
        }
        return PaymentApprovalResult.approved("FAKEPG-" + UUID.randomUUID().toString().replace("-", ""));
    }
}
