package com.ticket.core.api.controller;

import com.ticket.support.passport.web.PassportArgumentResolver;
import com.ticket.core.config.admission.AdmissionTokenValidator;
import com.ticket.core.domain.order.command.cancel.CancelOrderUseCase;
import com.ticket.core.domain.order.command.create.CreateOrderUseCase;
import com.ticket.core.domain.order.query.GetOrderDetailUseCase;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.support.ApiControllerAdvice;
import com.ticket.support.passport.Passport;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class OrderControllerContractTest {

    private static final Passport MEMBER = new Passport(100L, "MEMBER");

    private final CreateOrderUseCase createOrderUseCase = Mockito.mock(CreateOrderUseCase.class);
    private final GetOrderDetailUseCase getOrderDetailUseCase = Mockito.mock(GetOrderDetailUseCase.class);
    private final CancelOrderUseCase cancelOrderUseCase = Mockito.mock(CancelOrderUseCase.class);
    private final AdmissionTokenValidator admissionTokenValidator = Mockito.mock(AdmissionTokenValidator.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(
                createOrderUseCase,
                getOrderDetailUseCase,
                cancelOrderUseCase,
                admissionTokenValidator
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PassportArgumentResolver())
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
    void 주문시작_성공시_201_헤더와_응답바디_계약을_지킨다() throws Exception {
        when(createOrderUseCase.execute(new CreateOrderUseCase.Input(10L, List.of(7L, 3L), 100L)))
                .thenReturn(new CreateOrderUseCase.Output(
                        "ORD-20260324",
                        OrderState.PENDING,
                        LocalDateTime.of(2026, 3, 24, 14, 10)
                ));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AdmissionTokenValidator.HEADER, "admission-token")
                        .content("""
                                {
                                  "performanceId": 10,
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
                .andExpect(jsonPath("$.error").isEmpty());

        verify(admissionTokenValidator).validate(10L, "admission-token");
    }

    @Test
    void 주문시작_실패시_검증오류를_응답_계약으로_내린다() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "performanceId": 10,
                                  "seatIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(createOrderUseCase);
        verifyNoInteractions(admissionTokenValidator);
    }

    @Test
    void 주문시작_요청에_performanceId가_없으면_검증오류를_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seatIds": [7, 3]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(createOrderUseCase);
        verifyNoInteractions(admissionTokenValidator);
    }
}
