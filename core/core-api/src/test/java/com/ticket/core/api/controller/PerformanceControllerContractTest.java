package com.ticket.core.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticket.core.config.AdmissionTokenValidator;
import com.ticket.core.config.LoginMemberArgumentResolver;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.member.model.Role;
import com.ticket.core.domain.performance.query.GetBookingEntryUseCase;
import com.ticket.core.domain.performance.query.GetPerformanceScheduleListUseCase;
import com.ticket.core.domain.performance.query.GetPerformanceSummaryUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatAvailabilityUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatStatusUseCase;
import com.ticket.core.support.ApiControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
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

    private static final MemberPrincipal MEMBER = new MemberPrincipal(100L, Role.MEMBER);

    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase = Mockito.mock(GetSeatAvailabilityUseCase.class);
    private final GetSeatStatusUseCase getSeatStatusUseCase = Mockito.mock(GetSeatStatusUseCase.class);
    private final GetBookingEntryUseCase getBookingEntryUseCase = Mockito.mock(GetBookingEntryUseCase.class);
    private final AdmissionTokenValidator admissionTokenValidator = Mockito.mock(AdmissionTokenValidator.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PerformanceController controller = new PerformanceController(
                getSeatAvailabilityUseCase,
                getSeatStatusUseCase,
                Mockito.mock(GetPerformanceSummaryUseCase.class),
                Mockito.mock(GetPerformanceScheduleListUseCase.class),
                getBookingEntryUseCase,
                admissionTokenValidator
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new ApiControllerAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MEMBER, null, MEMBER.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void booking_entry_returns_ticket_server_decision() throws Exception {
        when(getBookingEntryUseCase.execute(new GetBookingEntryUseCase.Input(10L)))
                .thenReturn(new GetBookingEntryUseCase.Output(
                        GetBookingEntryUseCase.EntryType.QUEUE,
                        true,
                        null,
                        "/api/v1/queue/performances/10/enter"
                ));

        mockMvc.perform(get("/api/v1/performances/10/booking-entry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.entryType").value("QUEUE"))
                .andExpect(jsonPath("$.data.queueRequired").value(true))
                .andExpect(jsonPath("$.data.queueEnterUrl").value("/api/v1/queue/performances/10/enter"));
    }

    @Test
    void seat_availability_validates_admission_token() throws Exception {
        when(getSeatAvailabilityUseCase.execute(new GetSeatAvailabilityUseCase.Input(10L)))
                .thenReturn(new GetSeatAvailabilityUseCase.Output(List.of()));

        mockMvc.perform(get("/api/v1/performances/10/seats/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));

        verify(admissionTokenValidator).verify(any(HttpServletRequest.class), eq(100L), eq(10L));
    }

    @Test
    void seat_status_validates_admission_token() throws Exception {
        when(getSeatStatusUseCase.execute(new GetSeatStatusUseCase.Input(10L)))
                .thenReturn(new GetSeatStatusUseCase.Output(List.of()));

        mockMvc.perform(get("/api/v1/performances/10/seats/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));

        verify(admissionTokenValidator).verify(any(HttpServletRequest.class), eq(100L), eq(10L));
    }
}