package com.ticket.core.infra.payment;

import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.model.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class FakePaymentGatewayClientTest {

    private static final PaymentApprovalCommand COMMAND = new PaymentApprovalCommand(
            "PAY-1", 10L, BigDecimal.valueOf(120000), PaymentMethod.CARD
    );

    @Test
    void 기본_설정이면_승인하고_승인번호를_발급한다() {
        final PaymentApprovalResult result = new FakePaymentGatewayClient(true).approve(COMMAND);

        assertThat(result.approved()).isTrue();
        assertThat(result.pgTransactionId()).startsWith("FAKEPG-");
        assertThat(result.failureCode()).isNull();
    }

    @Test
    void 거절_설정이면_실패사유를_담아_거절한다() {
        final PaymentApprovalResult result = new FakePaymentGatewayClient(false).approve(COMMAND);

        assertThat(result.approved()).isFalse();
        assertThat(result.pgTransactionId()).isNull();
        assertThat(result.failureCode()).isEqualTo("FAKE_DECLINED");
        assertThat(result.failureMessage()).isNotBlank();
    }
}
