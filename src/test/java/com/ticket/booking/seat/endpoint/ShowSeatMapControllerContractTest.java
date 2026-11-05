package com.ticket.booking.seat.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ticket.booking.seat.usecase.GetShowSeatMapUseCase;

import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("NonAsciiCharacters")
class ShowSeatMapControllerContractTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GetShowSeatMapUseCase useCase = org.mockito.Mockito.mock(GetShowSeatMapUseCase.class);
        when(useCase.execute(new GetShowSeatMapUseCase.Input(1L)))
                .thenReturn(new GetShowSeatMapUseCase.Output(List.of(new GetShowSeatMapUseCase.SeatResponse(
                        101L, 1, "가", "A", "1", 10.0, 20.0, "VIP", "VIP석", BigDecimal.valueOf(170000)))));
        mockMvc = MockMvcBuilders.standaloneSetup(new ShowSeatMapController(
                        new ShowSeatMapBody(useCase, JsonMapper.builder().build())))
                .build();
    }

    @Test
    void 기존_프론트의_공연별_좌석_배치도_계약을_제공한다() throws Exception {
        mockMvc.perform(get("/api/v1/shows/1/seats"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_ENCODING))
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.seats[0].seatId").value(101))
                .andExpect(jsonPath("$.data.seats[0].gradeCode").value("VIP"))
                .andExpect(jsonPath("$.data.seats[0].gradeName").value("VIP석"))
                .andExpect(jsonPath("$.data.seats[0].price").value(170000));
    }

    @Test
    void gzip을_받는_요청에는_같은_본문을_미리_압축해_보낸다() throws Exception {
        final byte[] plain = mockMvc.perform(get("/api/v1/shows/1/seats"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        final byte[] gzipped = mockMvc.perform(
                        get("/api/v1/shows/1/seats").header(HttpHeaders.ACCEPT_ENCODING, "gzip, br"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_ENCODING, "gzip"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            assertThat(in.readAllBytes()).isEqualTo(plain);
        }
    }
}
