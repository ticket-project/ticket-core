package com.ticket.booking.seat.endpoint;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ticket.booking.exception.handler.BookingExceptionHandler;
import com.ticket.booking.seat.usecase.GetOccupiedSeatsUseCase;
import com.ticket.booking.seat.usecase.GetPerformanceSeatMapUseCase;
import com.ticket.booking.seat.usecase.GetSeatAvailabilityUseCase;
import com.ticket.booking.seat.usecase.GetSeatStatusUseCase;
import com.ticket.booking.seat.usecase.GetSeatStatusUseCase.SeatStatus;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.exception.handler.SecurityExceptionHandler;
import com.ticket.security.http.AuthenticatedMemberArgumentResolver;
import com.ticket.shared.exception.handler.GlobalExceptionHandler;
import com.ticket.show.api.PerformanceSummarySnapshot;

class PerformanceSeatQueryControllerContractTest {
    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(100L, "MEMBER");
    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase =
            Mockito.mock(GetSeatAvailabilityUseCase.class);
    private final GetSeatStatusUseCase getSeatStatusUseCase = Mockito.mock(GetSeatStatusUseCase.class);
    private final GetOccupiedSeatsUseCase getOccupiedSeatsUseCase = Mockito.mock(GetOccupiedSeatsUseCase.class);
    private final GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase =
            Mockito.mock(GetPerformanceSeatMapUseCase.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PerformanceSeatQueryController controller = new PerformanceSeatQueryController(
                getSeatAvailabilityUseCase,
                getSeatStatusUseCase,
                getOccupiedSeatsUseCase,
                getPerformanceSeatMapUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(
                        new GlobalExceptionHandler(), new BookingExceptionHandler(), new SecurityExceptionHandler())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(MEMBER, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void seat_map은_admission_token_없이_조회한다() throws Exception {
        when(getPerformanceSeatMapUseCase.execute(new GetPerformanceSeatMapUseCase.Input(10L)))
                .thenReturn(new GetPerformanceSeatMapUseCase.Output(
                        new GetPerformanceSeatMapUseCase.VenueResponse(1L, "venue", 500, 356, 4.8), List.of()));
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
    void seat_status는_admission_token을_넘기고_좌석_상태를_돌려준다() throws Exception {
        when(getSeatStatusUseCase.execute(new GetSeatStatusUseCase.Input(10L, 100L, "admission-token")))
                .thenReturn(new GetSeatStatusUseCase.Output(
                        List.of(new GetSeatStatusUseCase.SeatResponse(1001L, 101L, SeatStatus.OCCUPIED))));

        mockMvc.perform(get("/api/v1/performances/10/seats/status").header("X-Admission-Token", "admission-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.seats[0].performanceSeatId").value(1001))
                .andExpect(jsonPath("$.data.seats[0].seatId").value(101))
                .andExpect(jsonPath("$.data.seats[0].status").value("OCCUPIED"));
    }

    @Test
    void seat_occupied는_admission_token을_넘기고_점유_좌석_번호와_회차_요약을_돌려준다() throws Exception {
        when(getOccupiedSeatsUseCase.execute(new GetOccupiedSeatsUseCase.Input(10L, 100L, "admission-token")))
                .thenReturn(new GetOccupiedSeatsUseCase.Output(
                        List.of(101L, 205L),
                        new PerformanceSummarySnapshot("싱어게인", "충청", LocalDateTime.of(2026, 3, 20, 19, 30))));

        mockMvc.perform(get("/api/v1/performances/10/seats/occupied").header("X-Admission-Token", "admission-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.occupiedSeatIds[0]").value(101))
                .andExpect(jsonPath("$.data.occupiedSeatIds[1]").value(205))
                .andExpect(jsonPath("$.data.summary.title").value("싱어게인"))
                .andExpect(jsonPath("$.data.summary.region").value("충청"))
                .andExpect(jsonPath("$.data.summary.startTime").exists());
    }
}
