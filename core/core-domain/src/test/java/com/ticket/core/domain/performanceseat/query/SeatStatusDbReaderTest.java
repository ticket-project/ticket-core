package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.performanceseat.query.model.SeatStateView;
import com.ticket.core.domain.performanceseat.query.model.SeatStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatStatusDbReaderTest {

    @Mock
    private SeatMapQueryRepository seatMapQueryRepository;

    @InjectMocks
    private SeatStatusDbReader reader;

    @Test
    void DB_좌석_스냅샷만_트랜잭션_경계_안에서_읽는다() {
        List<SeatStateView> states = List.of(new SeatStateView(1L, SeatStatus.AVAILABLE));
        when(seatMapQueryRepository.findSeatStatuses(10L)).thenReturn(states);

        assertThat(reader.read(10L)).isSameAs(states);
    }
}
