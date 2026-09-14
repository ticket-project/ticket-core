package com.ticket.booking.endpoint;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ticket.booking.application.usecase.GetShowSeatMapUseCase;

@SuppressWarnings("NonAsciiCharacters")
class ShowSeatMapControllerContractTest {
    @Test
    void 기존_프론트의_공연별_좌석_배치도_계약을_제공한다() throws Exception {
        GetShowSeatMapUseCase useCase = org.mockito.Mockito.mock(GetShowSeatMapUseCase.class);
        when(useCase.execute(new GetShowSeatMapUseCase.Input(1L)))
                .thenReturn(
                        new GetShowSeatMapUseCase.Output(
                                List.of(
                                        new GetShowSeatMapUseCase.SeatMapEntry(
                                                101L,
                                                1,
                                                "가",
                                                "A",
                                                "1",
                                                10.0,
                                                20.0,
                                                "VIP",
                                                "VIP석",
                                                BigDecimal.valueOf(170000)))));
        MockMvc mockMvc =
                MockMvcBuilders.standaloneSetup(new ShowSeatMapController(useCase)).build();

        mockMvc.perform(get("/api/v1/shows/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats[0].seatId").value(101))
                .andExpect(jsonPath("$.data.seats[0].gradeCode").value("VIP"))
                .andExpect(jsonPath("$.data.seats[0].gradeName").value("VIP석"))
                .andExpect(jsonPath("$.data.seats[0].price").value(170000));
    }
}
