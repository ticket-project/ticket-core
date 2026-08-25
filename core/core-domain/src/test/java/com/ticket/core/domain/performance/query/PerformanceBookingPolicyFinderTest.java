package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceBookingPolicyFinderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Mock
    private PerformanceBookingPolicyQueryRepository queryRepository;

    @InjectMocks
    private PerformanceBookingPolicyFinder finder;

    @Test
    void 회차가_있으면_불변_정책을_반환한다() {
        PerformanceBookingPolicyView policy = policy(NOW.minusMinutes(1), NOW.plusMinutes(1));
        when(queryRepository.findByPerformanceId(10L)).thenReturn(Optional.of(policy));

        assertThat(finder.findById(10L)).isSameAs(policy);
    }

    @Test
    void 회차가_없으면_실패한다() {
        when(queryRepository.findByPerformanceId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> finder.findById(10L))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void 조회는_예매_가능_시각을_판정하지_않는다() {
        PerformanceBookingPolicyView closed = policy(NOW.minusHours(2), NOW.minusHours(1));
        when(queryRepository.findByPerformanceId(10L)).thenReturn(Optional.of(closed));

        assertThat(finder.findById(10L)).isSameAs(closed);
    }

    private PerformanceBookingPolicyView policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime
    ) {
        return new PerformanceBookingPolicyView(
                10L,
                orderOpenTime,
                orderCloseTime,
                4,
                300,
                null,
                null,
                null,
                null,
                null
        );
    }
}
