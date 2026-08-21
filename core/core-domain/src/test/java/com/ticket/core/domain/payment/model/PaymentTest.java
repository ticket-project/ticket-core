package com.ticket.core.domain.payment.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PaymentTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Test
    void 생성하면_READY_상태가_된다() {
        final Payment payment = payment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getPaymentKey()).isEqualTo("PAY-1");
        assertThat(payment.getOrderId()).isEqualTo(10L);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(120000));
        assertThat(payment.getApprovedAt()).isNull();
    }

    @Test
    void 승인하면_APPROVED로_전이하고_승인정보를_남긴다() {
        final Payment payment = payment();

        payment.approve(FIXED_NOW, "FAKEPG-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(FIXED_NOW);
        assertThat(payment.getPgTransactionId()).isEqualTo("FAKEPG-1");
        assertThat(payment.isApproved()).isTrue();
    }

    @Test
    void 거절하면_FAILED로_전이하고_실패사유를_남긴다() {
        final Payment payment = payment();

        payment.fail(FIXED_NOW, "FAKE_DECLINED", "가짜 게이트웨이가 승인을 거절했습니다.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(FIXED_NOW);
        assertThat(payment.getFailureCode()).isEqualTo("FAKE_DECLINED");
        assertThat(payment.getFailureMessage()).isEqualTo("가짜 게이트웨이가 승인을 거절했습니다.");
    }

    @Test
    void 이미_승인된_결제는_다시_승인할_수_없다() {
        final Payment payment = payment();
        payment.approve(FIXED_NOW, "FAKEPG-1");

        assertThatThrownBy(() -> payment.approve(FIXED_NOW, "FAKEPG-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY");
    }

    @Test
    void 실패한_결제는_승인할_수_없다() {
        final Payment payment = payment();
        payment.fail(FIXED_NOW, "FAKE_DECLINED", "거절");

        assertThatThrownBy(() -> payment.approve(FIXED_NOW, "FAKEPG-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 실패메시지가_200자를_넘으면_잘라서_저장한다() {
        final Payment payment = payment();

        payment.fail(FIXED_NOW, "FAKE_DECLINED", "가".repeat(300));

        assertThat(payment.getFailureMessage()).hasSize(200);
    }

    private Payment payment() {
        return new Payment("PAY-1", 10L, PaymentMethod.CARD, BigDecimal.valueOf(120000));
    }
}
