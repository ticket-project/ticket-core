package com.ticket.booking.web;

import com.ticket.member.AuthenticatedMember;
import com.ticket.member.infrastructure.AuthenticatedMemberArgumentResolver;
import com.ticket.booking.application.usecase.CancelOrderUseCase;
import com.ticket.booking.application.usecase.CreateOrderUseCase;
import com.ticket.booking.domain.OrderState;
import com.ticket.booking.application.usecase.GetOrderDetailUseCase;
import com.ticket.booking.application.usecase.GetOrderStatusUseCase;
import com.ticket.error.handler.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class OrderControllerContractTest {

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(100L, "MEMBER");

    private final CreateOrderUseCase createOrderUseCase = Mockito.mock(CreateOrderUseCase.class);
    private final GetOrderDetailUseCase getOrderDetailUseCase = Mockito.mock(GetOrderDetailUseCase.class);
    private final CancelOrderUseCase cancelOrderUseCase = Mockito.mock(CancelOrderUseCase.class);
    private final GetOrderStatusUseCase getOrderStatusUseCase = Mockito.mock(GetOrderStatusUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(
                createOrderUseCase,
                getOrderDetailUseCase,
                cancelOrderUseCase,
                getOrderStatusUseCase
        );
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
    void 주문시작_성공시_201_헤더와_응답바디_계약을_지킨다() throws Exception {
        when(createOrderUseCase.execute(new CreateOrderUseCase.Input(10L, List.of(7L, 3L), 100L, "admission-token")))
                .thenReturn(new CreateOrderUseCase.Output(
                        "ORD-20260324",
                        OrderState.PENDING,
                        LocalDateTime.of(2026, 3, 24, 14, 10),
                        600L
                ));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Admission-Token", "admission-token")
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
                .andExpect(jsonPath("$.data.remainingSeconds").value(600L))
                .andExpect(jsonPath("$.error").isEmpty());

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
    }

    @Test
    void 주문상태_조회는_경량응답_계약을_지킨다() throws Exception {
        when(getOrderStatusUseCase.execute(new GetOrderStatusUseCase.Input("order-key", 100L)))
                .thenReturn(new GetOrderStatusUseCase.Output(
                        "order-key",
                        OrderState.PENDING,
                        LocalDateTime.of(2026, 3, 24, 14, 10),
                        300L
                ));

        mockMvc.perform(get("/api/v1/orders/order-key/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderKey").value("order-key"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-03-24T14:10:00"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(300L));

        verify(getOrderStatusUseCase).execute(new GetOrderStatusUseCase.Input("order-key", 100L));
    }

    @Test
    void orderKey가_공백이면_400_계약을_지킨다() throws Exception {
        mockMvc.perform(get("/api/v1/orders/ "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getOrderDetailUseCase);
    }
}
