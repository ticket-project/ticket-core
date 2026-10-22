package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticket.shared.config.CacheConfig;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

/** 주문마다 읽던 회차 단위 표시값(회차·공연, 공연장, 등급)을 회차별로 한 번만 읽는지 본다. */
@SuppressWarnings("NonAsciiCharacters")
@SpringJUnitConfig({CacheConfig.class, PerformanceSaleHeaderReader.class})
class PerformanceSaleHeaderReaderCacheTest {
    @MockitoBean
    private ShowQuerydslRepository showQuerydslRepository;

    @MockitoBean
    private VenueLookupApi venueLookupApi;

    @Autowired
    private PerformanceSaleHeaderReader reader;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager
                .getCacheNames()
                .forEach(name ->
                        Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    @Test
    void 같은_회차는_캐시가_살아_있는_동안_회차_공연장_등급을_한_번만_읽는다() {
        when(showQuerydslRepository.findPerformanceShow(1L))
                .thenReturn(Optional.of(new PerformanceShowRow(2L, LocalDateTime.of(2026, 3, 20, 19, 30), "show", 3L)));
        when(venueLookupApi.getVenueSnapshot(3L))
                .thenReturn(new VenueSnapshot(
                        3L, "공연장", null, null, null, null, null, null, new VenueSnapshot.SeatMapLayout(0, 0, 0.0)));
        when(showQuerydslRepository.findPerformanceGrades(1L)).thenReturn(List.of());

        final PerformanceSaleHeaderReader.Header first = reader.read(1L);
        final PerformanceSaleHeaderReader.Header second = reader.read(1L);

        assertThat(second).isSameAs(first);
        assertThat(first.showTitle()).isEqualTo("show");
        assertThat(first.venueName()).isEqualTo("공연장");
        verify(showQuerydslRepository, times(1)).findPerformanceShow(1L);
        verify(venueLookupApi, times(1)).getVenueSnapshot(3L);
        verify(showQuerydslRepository, times(1)).findPerformanceGrades(1L);
    }
}
