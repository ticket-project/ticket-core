package com.ticket.booking.seat.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.booking.seat.domain.PerformanceSeatStateRow;
import com.ticket.shared.config.CacheConfig;

/** 좌석 상태 요청마다 읽던 회차 좌석 전체를 회차별로 한 번만 읽는지 본다. */
@SuppressWarnings("NonAsciiCharacters")
@SpringJUnitConfig({CacheConfig.class, PerformanceSeatIdsReader.class})
class PerformanceSeatIdsReaderCacheTest {
    @MockitoBean
    private PerformanceSeatRepository performanceSeatRepository;

    @Autowired
    private PerformanceSeatIdsReader reader;

    @Test
    void 같은_회차의_좌석_목록은_한_번만_읽고_다른_회차는_따로_읽는다() {
        when(performanceSeatRepository.findStatesByPerformanceId(10L))
                .thenReturn(List.of(new PerformanceSeatStateRow(101L, 1L, PerformanceSeatState.RESERVED)));
        when(performanceSeatRepository.findStatesByPerformanceId(20L)).thenReturn(List.of());

        final List<PerformanceSeatIdsReader.SeatIds> first = reader.read(10L);
        final List<PerformanceSeatIdsReader.SeatIds> second = reader.read(10L);
        reader.read(20L);

        assertThat(first).containsExactly(new PerformanceSeatIdsReader.SeatIds(101L, 1L));
        assertThat(second).isSameAs(first);
        verify(performanceSeatRepository, times(1)).findStatesByPerformanceId(10L);
        verify(performanceSeatRepository, times(1)).findStatesByPerformanceId(20L);
    }
}
