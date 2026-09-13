package com.ticket.booking.seat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AvailablePerformanceSeatReaderTest {
    @Mock private PerformanceSeatRepository performanceSeatRepository;
    @InjectMocks private AvailablePerformanceSeatReader availablePerformanceSeatReader;

    @Test
    void 가용한_공연좌석만_조회한다() {
        // given
        List<PerformanceSeat> seats = List.of(org.mockito.Mockito.mock(PerformanceSeat.class));
        when(performanceSeatRepository.findAllByStateEquals(PerformanceSeatState.AVAILABLE))
                .thenReturn(seats);
        // when
        List<PerformanceSeat> result = availablePerformanceSeatReader.readAllAvailable();
        // then
        assertThat(result).isEqualTo(seats);
        verify(performanceSeatRepository).findAllByStateEquals(PerformanceSeatState.AVAILABLE);
    }
}
