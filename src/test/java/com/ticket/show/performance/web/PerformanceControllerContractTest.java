package com.ticket.show.performance.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticket.show.performance.application.usecase.GetPerformanceScheduleListUseCase;
import com.ticket.show.performance.application.usecase.GetPerformanceSummaryUseCase;
import com.ticket.error.handler.GlobalExceptionHandler;
import com.ticket.show.support.exception.handler.ShowExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PerformanceControllerContractTest {

    private final GetPerformanceSummaryUseCase getPerformanceSummaryUseCase =
            Mockito.mock(GetPerformanceSummaryUseCase.class);

    private MockMvc newMockMvc() {
        PerformanceController controller = new PerformanceController(
                getPerformanceSummaryUseCase,
                Mockito.mock(GetPerformanceScheduleListUseCase.class)
        );
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ShowExceptionHandler())
                .build();
    }

    @Test
    void booking_entry_api는_제공하지_않는다() throws Exception {
        newMockMvc().perform(get("/api/v1/performances/10/booking-entry"))
                .andExpect(status().isNotFound());
    }

    @Test
    void performanceId가_양수가_아니면_400_계약을_지킨다() throws Exception {
        newMockMvc().perform(get("/api/v1/performances/-1/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getPerformanceSummaryUseCase);
    }
}
