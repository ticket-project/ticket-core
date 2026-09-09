package com.ticket.booking.web;

import com.ticket.member.infrastructure.AuthenticatedMemberArgumentResolver;
import com.ticket.booking.application.usecase.CreateOrderUseCase;
import com.ticket.booking.domain.OrderState;
import com.ticket.error.handler.GlobalExceptionHandler;
import com.ticket.member.AuthenticatedMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class HoldControllerContractTest {

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(100L, "MEMBER");

    private final CreateOrderUseCase createOrderUseCase = Mockito.mock(CreateOrderUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HoldController controller = new HoldController(createOrderUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
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
    void hold_생성_성공시_기존_계약을_유지한다() throws Exception {
        when(createOrderUseCase.execute(new CreateOrderUseCase.Input(10L, List.of(7L, 3L), 100L, "admission-token")))
                .thenReturn(new CreateOrderUseCase.Output(
                        "ORD-20260324",
                        OrderState.PENDING,
                        LocalDateTime.of(2026, 3, 24, 14, 10),
                        600L
                ));

        mockMvc.perform(post("/api/v1/performances/10/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Admission-Token", "admission-token")
                        .content("""
                                {
                                  "seatIds": [7, 3]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/ORD-20260324"))
                .andExpect(header().string("X-Order-Key", "ORD-20260324"))
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderKey").value("ORD-20260324"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-03-24T14:10:00"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(600L))
                .andExpect(jsonPath("$.error").isEmpty());

    }

    @Test
    void hold_생성_실패시_검증오류_응답_계약을_유지한다() throws Exception {
        mockMvc.perform(post("/api/v1/performances/10/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seatIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(createOrderUseCase);
    }

    @Test
    void performanceId가_양수가_아니면_400_계약을_지킨다() throws Exception {
        mockMvc.perform(post("/api/v1/performances/-1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seatIds": [7]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(createOrderUseCase);
    }
}
