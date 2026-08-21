package com.ticket.core.domain.payment.command;

import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.payment.gateway.PaymentApprovalCommand;
import com.ticket.core.domain.payment.gateway.PaymentApprovalResult;
import com.ticket.core.domain.payment.gateway.PaymentGatewayClient;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class ConfirmPaymentUseCaseTest {

    private static final BigDecimal AMOUNT = BigDecimal.valueOf(120000);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);
    private static final PaymentApprovalCommand EXPECTED_COMMAND = new PaymentApprovalCommand(
            "PAY-1", 10L, AMOUNT, PaymentMethod.CARD
    );

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentConfirmationTxService txService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-21T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 승인되면_확정_트랜잭션을_호출하고_결과를_반환한다() {
        when(txService.loadConfirmable("PAY-1", 1L, AMOUNT, FIXED_NOW)).thenReturn(confirmable(false));
        when(paymentGatewayClient.approve(EXPECTED_COMMAND))
                .thenReturn(PaymentApprovalResult.approved("FAKEPG-1"));
        when(txService.approve(20L, "FAKEPG-1", FIXED_NOW)).thenReturn(new ConfirmPaymentUseCase.Output(
                "PAY-1", PaymentStatus.APPROVED, FIXED_NOW, "order-key", "CONFIRMED"
        ));

        final ConfirmPaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(output.orderStatus()).isEqualTo("CONFIRMED");
        verify(memberFinder).findActiveMemberById(1L);
    }

    @Test
    void 이미_승인된_결제면_게이트웨이를_호출하지_않고_같은_결과를_반환한다() {
        when(txService.loadConfirmable("PAY-1", 1L, AMOUNT, FIXED_NOW)).thenReturn(confirmable(true));

        final ConfirmPaymentUseCase.Output output = useCase().execute(input());

        assertThat(output.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(output.approvedAt()).isEqualTo(FIXED_NOW);
        verifyNoInteractions(paymentGatewayClient);
    }

    @Test
    void 거절되면_실패를_기록하고_예외를_던진다() {
        when(txService.loadConfirmable("PAY-1", 1L, AMOUNT, FIXED_NOW)).thenReturn(confirmable(false));
        when(paymentGatewayClient.approve(EXPECTED_COMMAND))
                .thenReturn(PaymentApprovalResult.declined("FAKE_DECLINED", "거절되었습니다."));

        assertThatThrownBy(() -> useCase().execute(input()))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.PAYMENT_DECLINED));

        verify(txService).fail(20L, "FAKE_DECLINED", "거절되었습니다.", FIXED_NOW);
    }

    @Test
    void execute는_DB_트랜잭션을_직접_시작하지_않는다() throws Exception {
        assertThat(ConfirmPaymentUseCase.class
                .getDeclaredMethod("execute", ConfirmPaymentUseCase.Input.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNull();
        assertThat(ConfirmPaymentUseCase.class
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNull();
    }

    private ConfirmPaymentUseCase useCase() {
        return new ConfirmPaymentUseCase(memberFinder, paymentGatewayClient, txService, fixedClock);
    }

    private ConfirmPaymentUseCase.Input input() {
        return new ConfirmPaymentUseCase.Input("PAY-1", 1L, AMOUNT);
    }

    private PaymentConfirmationTxService.ConfirmablePayment confirmable(final boolean alreadyApproved) {
        return new PaymentConfirmationTxService.ConfirmablePayment(
                20L,
                "PAY-1",
                10L,
                PaymentMethod.CARD,
                AMOUNT,
                alreadyApproved,
                alreadyApproved ? FIXED_NOW : null,
                "order-key",
                alreadyApproved ? "CONFIRMED" : "PENDING"
        );
    }
}
