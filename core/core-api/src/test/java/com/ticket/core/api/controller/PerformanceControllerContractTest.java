package com.ticket.core.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticket.core.config.security.AuthenticatedMemberArgumentResolver;
import com.ticket.core.api.AdmissionHeaders;
import com.ticket.core.app.performance.query.GetPerformanceScheduleListUseCase;
import com.ticket.core.app.performance.query.GetPerformanceSummaryUseCase;
import com.ticket.core.app.performanceseat.query.GetSeatAvailabilityUseCase;
import com.ticket.core.app.performanceseat.query.GetSeatStatusUseCase;
import com.ticket.core.api.error.GlobalExceptionHandler;
import com.ticket.core.app.auth.token.AuthenticatedMember;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PerformanceControllerContractTest {

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(100L, "MEMBER");

    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase = Mockito.mock(GetSeatAvailabilityUseCase.class);
    private final GetSeatStatusUseCase getSeatStatusUseCase = Mockito.mock(GetSeatStatusUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PerformanceController controller = new PerformanceController(
                getSeatAvailabilityUseCase,
                getSeatStatusUseCase,
                Mockito.mock(GetPerformanceSummaryUseCase.class),
                Mockito.mock(GetPerformanceScheduleListUseCase.class)
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
    void booking_entry_api는_제공하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/performances/10/booking-entry"))
                .andExpect(status().isNotFound());
    }

    @Test
    void seat_availability는_admission_token_없이_조회한다() throws Exception {
        when(getSeatAvailabilityUseCase.execute(new GetSeatAvailabilityUseCase.Input(10L)))
                .thenReturn(new GetSeatAvailabilityUseCase.Output(List.of()));
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/performances/10/seats/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));

    }

    @Test
    void seat_status는_admission_token_validator를_거친다() throws Exception {
        when(getSeatStatusUseCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .thenReturn(new GetSeatStatusUseCase.Output(List.of()));

        mockMvc.perform(get("/api/v1/performances/10/seats/status")
                        .header(AdmissionHeaders.ADMISSION_TOKEN, "admission-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));

    }
}
