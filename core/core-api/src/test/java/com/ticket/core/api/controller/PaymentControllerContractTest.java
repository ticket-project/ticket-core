package com.ticket.core.api.controller;

import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.config.security.MemberPrincipalArgumentResolver;
import com.ticket.core.domain.payment.command.ConfirmPaymentUseCase;
import com.ticket.core.domain.payment.command.PreparePaymentUseCase;
import com.ticket.core.domain.payment.model.PaymentMethod;
import com.ticket.core.domain.payment.model.PaymentStatus;
import com.ticket.core.support.ApiControllerAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class PaymentControllerContractTest {

    private static final MemberPrincipal MEMBER = new MemberPrincipal(100L, "MEMBER");
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    private final PreparePaymentUseCase preparePaymentUseCase = Mockito.mock(PreparePaymentUseCase.class);
    private final ConfirmPaymentUseCase confirmPaymentUseCase = Mockito.mock(ConfirmPaymentUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentController(preparePaymentUseCase, confirmPaymentUseCase))
                .setCustomArgumentResolvers(new MemberPrincipalArgumentResolver())
                .setControllerAdvice(new ApiControllerAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MEMBER, null, java.util.List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 결제_준비_API는_201과_준비된_결제를_반환한다() throws Exception {
        when(preparePaymentUseCase.execute(any(PreparePaymentUseCase.Input.class)))
                .thenReturn(new PreparePaymentUseCase.Output(
                        "PAY-1", "order-key", BigDecimal.valueOf(120000), PaymentStatus.READY, FIXED_NOW.plusMinutes(5)
                ));

        mockMvc.perform(post("/api/v1/orders/order-key/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"amount\":120000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentKey").value("PAY-1"))
                .andExpect(jsonPath("$.data.status").value("READY"));

        verify(preparePaymentUseCase).execute(new PreparePaymentUseCase.Input(
                "order-key", 100L, PaymentMethod.CARD, BigDecimal.valueOf(120000)
        ));
    }

    @Test
    void 결제_승인_API는_200과_확정된_주문_상태를_반환한다() throws Exception {
        when(confirmPaymentUseCase.execute(any(ConfirmPaymentUseCase.Input.class)))
                .thenReturn(new ConfirmPaymentUseCase.Output(
                        "PAY-1", PaymentStatus.APPROVED, FIXED_NOW, "order-key", "CONFIRMED"
                ));

        mockMvc.perform(post("/api/v1/payments/PAY-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":120000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"));

        verify(confirmPaymentUseCase).execute(new ConfirmPaymentUseCase.Input(
                "PAY-1", 100L, BigDecimal.valueOf(120000)
        ));
    }

    @Test
    void 금액이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/PAY-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
