package com.ticket.payment.domain.payment.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PaymentTest {

    @Test
    void 결제_시도를_생성하면_ready_상태로_초기화된다() {
        //given
        LocalDateTime requestedAt = LocalDateTime.of(2026, 3, 15, 12, 0);

        //when
        Payment payment = createPayment(requestedAt);

        //then
        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key");
        assertThat(payment.getAttemptNo()).isEqualTo(1);
        assertThat(payment.getProvider()).isEqualTo("TOSS");
        assertThat(payment.getMethod()).isEqualTo("CARD");
        assertThat(payment.getAmount()).isEqualByComparingTo("15000");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(payment.isTerminal()).isFalse();
    }

    @Test
    void ready_상태는_processing으로_전이할_수_있다() {
        //given
        Payment payment = createPayment(LocalDateTime.of(2026, 3, 15, 12, 0));

        //when
        payment.process();

        //then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void processing이_아닌_결제는_다시_process할_수_없다() {
        //given
        Payment payment = createPayment(LocalDateTime.of(2026, 3, 15, 12, 0));
        payment.process();

        //when
        //then
        assertThatThrownBy(payment::process)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=PROCESSING");
    }

    @Test
    void ready_상태에서_바로_승인할_수_있다() {
        //given
        LocalDateTime approvedAt = LocalDateTime.of(2026, 3, 15, 12, 5);
        Payment payment = createPayment(LocalDateTime.of(2026, 3, 15, 12, 0));

        //when
        payment.approve("provider-payment-key", approvedAt);

        //then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentKey()).isEqualTo("provider-payment-key");
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(payment.isTerminal()).isTrue();
    }

    @Test
    void processing_상태에서_실패할_수_있다() {
        //given
        LocalDateTime failedAt = LocalDateTime.of(2026, 3, 15, 12, 5);
        Payment payment = createPayment(LocalDateTime.of(2026, 3, 15, 12, 0));
        payment.process();

        //when
        payment.fail("PG_DECLINED", "한도 초과", failedAt);

        //then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("PG_DECLINED");
        assertThat(payment.getFailureMessage()).isEqualTo("한도 초과");
        assertThat(payment.getFailedAt()).isEqualTo(failedAt);
        assertThat(payment.isTerminal()).isTrue();
    }

    @Test
    void ready_상태에서_취소할_수_있다() {
        //given
        LocalDateTime canceledAt = LocalDateTime.of(2026, 3, 15, 12, 5);
        Payment payment = createPayment(LocalDateTime.of(2026, 3, 15, 12, 0));

        //when
        payment.cancel(canceledAt);

        //then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(payment.isTerminal()).isTrue();
    }

    @Test
    void 종결_상태의_결제는_다시_전이할_수_없다() {
        //given
        Payment payment = createPayment(LocalDateTime.of(2026, 3, 15, 12, 0));
        payment.approve("provider-payment-key", LocalDateTime.of(2026, 3, 15, 12, 5));

        //when
        //then
        assertThatThrownBy(() -> payment.fail("CODE", "메시지", LocalDateTime.of(2026, 3, 15, 12, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=SUCCEEDED");
        assertThatThrownBy(() -> payment.cancel(LocalDateTime.of(2026, 3, 15, 12, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=SUCCEEDED");
    }

    private Payment createPayment(final LocalDateTime requestedAt) {
        return Payment.request(1L, "payment-key", 1, "TOSS", "CARD", BigDecimal.valueOf(15000), requestedAt);
    }
}
