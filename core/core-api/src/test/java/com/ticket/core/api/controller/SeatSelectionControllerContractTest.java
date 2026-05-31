package com.ticket.core.api.controller;

import com.ticket.support.passport.web.PassportArgumentResolver;
import com.ticket.core.config.admission.AdmissionTokenValidator;
import com.ticket.core.domain.performanceseat.command.DeselectAllSeatsUseCase;
import com.ticket.core.domain.performanceseat.command.DeselectSeatUseCase;
import com.ticket.core.domain.performanceseat.command.SelectSeatUseCase;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionControllerContractTest {

    private static final Passport MEMBER = new Passport(100L, "MEMBER");

    private final AdmissionTokenValidator admissionTokenValidator = Mockito.mock(AdmissionTokenValidator.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SeatSelectionController controller = new SeatSelectionController(
                Mockito.mock(SelectSeatUseCase.class),
                Mockito.mock(DeselectSeatUseCase.class),
                Mockito.mock(DeselectAllSeatsUseCase.class),
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
    void 좌석_선택_API는_200과_성공_응답_계약을_유지한다() throws Exception {
        mockMvc.perform(post("/api/v1/performances/10/seats/20/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AdmissionTokenValidator.HEADER, "admission-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty());

        verify(admissionTokenValidator).validate(10L, "admission-token");
    }

    @Test
    void 좌석_선택_해제_API는_200과_성공_응답_계약을_유지한다() throws Exception {
        mockMvc.perform(delete("/api/v1/performances/10/seats/20/select")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty());

        verifyNoInteractions(admissionTokenValidator);
    }

    @Test
    void 내_선택_좌석_전체_해제_API는_200과_성공_응답_계약을_유지한다() throws Exception {
        mockMvc.perform(delete("/api/v1/performances/10/seats/select")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty());

        verifyNoInteractions(admissionTokenValidator);
    }
}
