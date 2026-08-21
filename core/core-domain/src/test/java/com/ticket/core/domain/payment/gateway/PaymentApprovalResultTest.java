package com.ticket.core.domain.payment.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PaymentApprovalResultTest {

    @Test
    void approved는_승인_결과를_만들고_실패_필드가_비어_있다() {
        final PaymentApprovalResult result = PaymentApprovalResult.approved("FAKEPG-1");

        assertThat(result.approved()).isTrue();
        assertThat(result.pgTransactionId()).isEqualTo("FAKEPG-1");
        assertThat(result.failureCode()).isNull();
    }

    @Test
    void declined는_거절_결과를_만들고_pgTransactionId가_비어_있다() {
        final PaymentApprovalResult result = PaymentApprovalResult.declined("CODE", "메시지");

        assertThat(result.approved()).isFalse();
        assertThat(result.pgTransactionId()).isNull();
        assertThat(result.failureCode()).isEqualTo("CODE");
        assertThat(result.failureMessage()).isEqualTo("메시지");
    }

    @Test
    void 승인인데_failureCode가_채워지면_예외를_던진다() {
        assertThatThrownBy(() -> new PaymentApprovalResult(true, "FAKEPG-1", "CODE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 거절인데_pgTransactionId가_채워지면_예외를_던진다() {
        assertThatThrownBy(() -> new PaymentApprovalResult(false, "FAKEPG-1", "CODE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
