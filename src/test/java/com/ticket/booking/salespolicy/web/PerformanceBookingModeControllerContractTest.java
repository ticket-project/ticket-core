package com.ticket.booking.salespolicy.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticket.booking.salespolicy.application.usecase.GetPerformanceBookingModeUseCase;
import com.ticket.booking.salespolicy.domain.OrderAcceptanceStatus;
import com.ticket.error.NotFoundException;
import com.ticket.error.handler.GlobalExceptionHandler;
import com.ticket.booking.exception.handler.BookingExceptionHandler;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 이 API는 인증 없이 조회된다({@code SecurityConfig}의 permitAll) — 이 계약 테스트는 controller
 * 응답 형태만 고정하고 인증 필터는 검증 대상이 아니다.
 */
class PerformanceBookingModeControllerContractTest {

    private final GetPerformanceBookingModeUseCase getPerformanceBookingModeUseCase =
            Mockito.mock(GetPerformanceBookingModeUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PerformanceBookingModeController controller =
                new PerformanceBookingModeController(getPerformanceBookingModeUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new BookingExceptionHandler())
                .build();
    }

    @Test
    void 접수중이고_대기열이_필요없으면_DIRECT를_반환한다() throws Exception {
        when(getPerformanceBookingModeUseCase.execute(new GetPerformanceBookingModeUseCase.Input(10L)))
                .thenReturn(new GetPerformanceBookingModeUseCase.Output(
                        10L,
                        OrderAcceptanceStatus.OPEN,
                        GetPerformanceBookingModeUseCase.BookingMode.DIRECT,
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        LocalDateTime.of(2026, 12, 31, 23, 59),
                        4,
                        300L
                ));

        mockMvc.perform(get("/api/v1/booking/performances/10/booking-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.acceptanceStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.bookingMode").value("DIRECT"))
                .andExpect(jsonPath("$.data.maxSeatCount").value(4))
                .andExpect(jsonPath("$.data.holdDurationSeconds").value(300));
    }

    @Test
    void 대기열이_필요하면_QUEUE를_반환한다() throws Exception {
        when(getPerformanceBookingModeUseCase.execute(new GetPerformanceBookingModeUseCase.Input(10L)))
                .thenReturn(new GetPerformanceBookingModeUseCase.Output(
                        10L,
                        OrderAcceptanceStatus.OPEN,
                        GetPerformanceBookingModeUseCase.BookingMode.QUEUE,
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        LocalDateTime.of(2026, 12, 31, 23, 59),
                        null,
                        300L
                ));

        mockMvc.perform(get("/api/v1/booking/performances/10/booking-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingMode").value("QUEUE"))
                .andExpect(jsonPath("$.data.maxSeatCount").doesNotExist());
    }

    @Test
    void 접수기간이_아니면_UNAVAILABLE을_반환한다() throws Exception {
        when(getPerformanceBookingModeUseCase.execute(new GetPerformanceBookingModeUseCase.Input(10L)))
                .thenReturn(new GetPerformanceBookingModeUseCase.Output(
                        10L,
                        OrderAcceptanceStatus.BEFORE_OPEN,
                        GetPerformanceBookingModeUseCase.BookingMode.UNAVAILABLE,
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        LocalDateTime.of(2026, 12, 31, 23, 59),
                        4,
                        300L
                ));

        mockMvc.perform(get("/api/v1/booking/performances/10/booking-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptanceStatus").value("BEFORE_OPEN"))
                .andExpect(jsonPath("$.data.bookingMode").value("UNAVAILABLE"));
    }

    @Test
    void 판매_정책이_없으면_404를_반환한다() throws Exception {
        when(getPerformanceBookingModeUseCase.execute(new GetPerformanceBookingModeUseCase.Input(10L)))
                .thenThrow(new NotFoundException("회차 판매 정책을 찾을 수 없습니다. id=10"));

        mockMvc.perform(get("/api/v1/booking/performances/10/booking-mode"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E404"));
    }
}
