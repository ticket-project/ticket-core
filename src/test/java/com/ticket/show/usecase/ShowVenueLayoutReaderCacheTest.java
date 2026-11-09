package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticket.shared.config.CacheConfig;

/** 예매 화면에 들어오는 사용자마다 DB 두 번을 읽던 venue-layout을 공연별로 한 번만 읽는지 본다. */
@SuppressWarnings("NonAsciiCharacters")
@SpringJUnitConfig({CacheConfig.class, ShowVenueLayoutReader.class})
class ShowVenueLayoutReaderCacheTest {
    @MockitoBean
    private GetShowVenueLayoutUseCase getShowVenueLayoutUseCase;

    @Autowired
    private ShowVenueLayoutReader reader;

    @Test
    void 같은_공연은_한_번만_읽고_다른_공연은_따로_읽는다() {
        final GetShowVenueLayoutUseCase.Output layout = new GetShowVenueLayoutUseCase.Output("올림픽홀", 1000, 800, 12.0);
        when(getShowVenueLayoutUseCase.execute(new GetShowVenueLayoutUseCase.Input(1L)))
                .thenReturn(layout);
        when(getShowVenueLayoutUseCase.execute(new GetShowVenueLayoutUseCase.Input(2L)))
                .thenReturn(layout);

        assertThat(reader.read(1L)).isEqualTo(layout);
        reader.read(1L);
        reader.read(2L);

        verify(getShowVenueLayoutUseCase, times(1)).execute(new GetShowVenueLayoutUseCase.Input(1L));
        verify(getShowVenueLayoutUseCase, times(1)).execute(new GetShowVenueLayoutUseCase.Input(2L));
    }
}
