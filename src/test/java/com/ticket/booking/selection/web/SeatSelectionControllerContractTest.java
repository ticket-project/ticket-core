package com.ticket.booking.selection.web;

import com.ticket.member.security.infrastructure.AuthenticatedMemberArgumentResolver;
import com.ticket.booking.selection.application.usecase.DeselectAllSeatsUseCase;
import com.ticket.booking.selection.application.usecase.DeselectSeatUseCase;
import com.ticket.booking.selection.application.usecase.SelectSeatUseCase;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionControllerContractTest {

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(100L, "MEMBER");

    private final SelectSeatUseCase selectSeatUseCase = Mockito.mock(SelectSeatUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SeatSelectionController controller = new SeatSelectionController(
                selectSeatUseCase,
                Mockito.mock(DeselectSeatUseCase.class),
                Mockito.mock(DeselectAllSeatsUseCase.class)
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
    void 좌석_선택_API는_200과_성공_응답_계약을_유지한다() throws Exception {
        mockMvc.perform(post("/api/v1/performances/10/seats/20/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Admission-Token", "admission-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void 좌석_선택_해제_API는_200과_성공_응답_계약을_유지한다() throws Exception {
        mockMvc.perform(delete("/api/v1/performances/10/seats/20/select")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void 내_선택_좌석_전체_해제_API는_200과_성공_응답_계약을_유지한다() throws Exception {
        mockMvc.perform(delete("/api/v1/performances/10/seats/select")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void seatId가_양수가_아니면_400_계약을_지킨다() throws Exception {
        mockMvc.perform(post("/api/v1/performances/1/seats/0/select"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(selectSeatUseCase);
    }
}
