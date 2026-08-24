package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceFinderTest {

    @Mock
    private PerformanceRepository performanceRepository;

    private PerformanceFinder performanceFinder;

    @BeforeEach
    void setUp() {
        this.performanceFinder = new PerformanceFinder(performanceRepository);
    }

    @Test
    void 공연이_있으면_대기열_정책과_함께_반환한다() {
        //given
        Performance performance = createPerformance();
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        Performance result = performanceFinder.findById(1L);

        //then
        assertThat(result).isSameAs(performance);
    }

    @Test
    void 공연이_없으면_찾을수없음_예외를_던진다() {
        //given
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> performanceFinder.findById(1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    private Performance createPerformance() {
        return new Performance(
                null,
                1L,
                LocalDateTime.of(2026, 3, 16, 20, 0),
                LocalDateTime.of(2026, 3, 16, 22, 0),
                LocalDateTime.of(2026, 3, 15, 18, 50),
                LocalDateTime.of(2026, 3, 15, 19, 10),
                4,
                300
        );
    }
}
