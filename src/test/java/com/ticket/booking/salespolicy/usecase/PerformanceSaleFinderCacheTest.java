package com.ticket.booking.salespolicy.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticket.booking.exception.PerformanceSalesPolicyNotFoundException;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.shared.config.CacheConfig;

/** 캐시는 Spring 프록시가 있어야 걸리므로 컨텍스트를 띄워 본다. 예매 진입 검사 세 번이 DB를 한 번만 읽는지가 요점이다. */
@SuppressWarnings("NonAsciiCharacters")
@SpringJUnitConfig({CacheConfig.class, PerformanceSaleFinder.class})
class PerformanceSaleFinderCacheTest {
    @MockitoBean
    private PerformanceSalesPolicyRepository repository;

    @Autowired
    private PerformanceSaleFinder finder;

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
    void 같은_회차는_캐시가_살아_있는_동안_DB를_한_번만_읽는다() {
        final PerformanceSalesPolicy policy = mock(PerformanceSalesPolicy.class);
        when(repository.findById(1L)).thenReturn(Optional.of(policy));

        assertThat(finder.requirePolicy(1L)).isSameAs(policy);
        assertThat(finder.requirePolicy(1L)).isSameAs(policy);
        assertThat(finder.requirePolicy(1L)).isSameAs(policy);

        verify(repository, times(1)).findById(1L);
    }

    @Test
    void 회차가_다르면_따로_읽는다() {
        when(repository.findById(1L)).thenReturn(Optional.of(mock(PerformanceSalesPolicy.class)));
        when(repository.findById(2L)).thenReturn(Optional.of(mock(PerformanceSalesPolicy.class)));

        assertThat(finder.requirePolicy(1L)).isNotSameAs(finder.requirePolicy(2L));

        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).findById(2L);
    }

    @Test
    void 판매_정책이_없는_회차는_예외를_그대로_던지고_캐시하지_않는다() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.requirePolicy(9L)).isInstanceOf(PerformanceSalesPolicyNotFoundException.class);
        assertThatThrownBy(() -> finder.requirePolicy(9L)).isInstanceOf(PerformanceSalesPolicyNotFoundException.class);

        verify(repository, times(2)).findById(9L);
    }
}
