package com.ticket.booking.seat.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticket.shared.config.CacheConfig;
import com.ticket.show.api.PerformanceLayoutApi;

/** 예매 화면에 들어오는 사용자마다 좌석 전체를 다시 읽던 배치도를 공연별로 한 번만 조합하는지 본다. */
@SuppressWarnings("NonAsciiCharacters")
@SpringJUnitConfig({CacheConfig.class, GetShowSeatMapUseCase.class})
class GetShowSeatMapUseCaseCacheTest {
    @MockitoBean
    private PerformanceLayoutApi performanceLayoutApi;

    @MockitoBean
    private GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase;

    @Autowired
    private GetShowSeatMapUseCase useCase;

    @Test
    void 같은_공연의_배치도는_한_번만_조합하고_다른_공연은_따로_조합한다() {
        when(performanceLayoutApi.findRepresentativePerformanceId(1L)).thenReturn(Optional.of(10L));
        when(performanceLayoutApi.findRepresentativePerformanceId(2L)).thenReturn(Optional.of(20L));
        when(getPerformanceSeatMapUseCase.execute(new GetPerformanceSeatMapUseCase.Input(10L)))
                .thenReturn(new GetPerformanceSeatMapUseCase.Output(null, List.of()));
        when(getPerformanceSeatMapUseCase.execute(new GetPerformanceSeatMapUseCase.Input(20L)))
                .thenReturn(new GetPerformanceSeatMapUseCase.Output(null, List.of()));

        final GetShowSeatMapUseCase.Output first = useCase.execute(new GetShowSeatMapUseCase.Input(1L));
        final GetShowSeatMapUseCase.Output second = useCase.execute(new GetShowSeatMapUseCase.Input(1L));
        useCase.execute(new GetShowSeatMapUseCase.Input(2L));

        assertThat(second).isSameAs(first);
        verify(performanceLayoutApi, times(1)).findRepresentativePerformanceId(1L);
        verify(getPerformanceSeatMapUseCase, times(1)).execute(new GetPerformanceSeatMapUseCase.Input(10L));
        verify(getPerformanceSeatMapUseCase, times(1)).execute(new GetPerformanceSeatMapUseCase.Input(20L));
    }
}
