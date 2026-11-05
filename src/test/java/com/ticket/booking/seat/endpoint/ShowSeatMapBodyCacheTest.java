package com.ticket.booking.seat.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticket.booking.seat.usecase.GetShowSeatMapUseCase;
import com.ticket.shared.config.CacheConfig;

import tools.jackson.databind.json.JsonMapper;

/** 예매 화면에 들어오는 사용자마다 조합·JSON 변환·압축하던 배치도를 공연별로 한 번만 만드는지 본다. */
@SuppressWarnings("NonAsciiCharacters")
@SpringJUnitConfig({CacheConfig.class, ShowSeatMapBody.class, ShowSeatMapBodyCacheTest.JsonConfig.class})
class ShowSeatMapBodyCacheTest {
    @MockitoBean
    private GetShowSeatMapUseCase getShowSeatMapUseCase;

    @Autowired
    private ShowSeatMapBody showSeatMapBody;

    @Test
    void 같은_공연의_본문은_한_번만_만들고_다른_공연은_따로_만든다() {
        when(getShowSeatMapUseCase.execute(new GetShowSeatMapUseCase.Input(1L)))
                .thenReturn(new GetShowSeatMapUseCase.Output(List.of()));
        when(getShowSeatMapUseCase.execute(new GetShowSeatMapUseCase.Input(2L)))
                .thenReturn(new GetShowSeatMapUseCase.Output(List.of()));

        final ShowSeatMapBody.Encoded first = showSeatMapBody.read(1L);
        final ShowSeatMapBody.Encoded second = showSeatMapBody.read(1L);
        showSeatMapBody.read(2L);

        assertThat(second).isSameAs(first);
        verify(getShowSeatMapUseCase, times(1)).execute(new GetShowSeatMapUseCase.Input(1L));
        verify(getShowSeatMapUseCase, times(1)).execute(new GetShowSeatMapUseCase.Input(2L));
    }

    static class JsonConfig {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
