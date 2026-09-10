package com.ticket.booking.seat.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticket.member.security.infrastructure.AuthenticatedMemberArgumentResolver;
import com.ticket.booking.seat.application.usecase.GetPerformanceSeatMapUseCase;
import com.ticket.booking.seat.application.usecase.GetSeatAvailabilityUseCase;
import com.ticket.booking.seat.application.usecase.GetSeatStatusUseCase;
import com.ticket.error.handler.GlobalExceptionHandler;
import com.ticket.member.AuthenticatedMember;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PerformanceSeatQueryControllerContractTest {

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(100L, "MEMBER");

    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase = Mockito.mock(GetSeatAvailabilityUseCase.class);
    private final GetSeatStatusUseCase getSeatStatusUseCase = Mockito.mock(GetSeatStatusUseCase.class);
    private final GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase = Mockito.mock(GetPerformanceSeatMapUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PerformanceSeatQueryController controller = new PerformanceSeatQueryController(
                getSeatAvailabilityUseCase,
                getSeatStatusUseCase,
                getPerformanceSeatMapUseCase
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
    void seat_map은_admission_token_없이_조회한다() throws Exception {
        when(getPerformanceSeatMapUseCase.execute(new GetPerformanceSeatMapUseCase.Input(10L)))
                .thenReturn(new GetPerformanceSeatMapUseCase.Output(
                        new GetPerformanceSeatMapUseCase.VenueView(1L, "venue", 500, 356, 4.8),
                        List.of()
                ));
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/performances/10/seat-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));
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
                        .header("X-Admission-Token", "admission-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));
    }
}
