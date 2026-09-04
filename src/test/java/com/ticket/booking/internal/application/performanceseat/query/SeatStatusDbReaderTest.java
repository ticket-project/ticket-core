package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.application.performanceseat.query.model.SeatStateSnapshotRow;
import com.ticket.booking.internal.application.performanceseat.query.model.SeatStatus;
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
class SeatStateSnapshotReaderTest {

    @Mock
    private SeatMapReadRepository seatMapReadRepository;

    @InjectMocks
    private SeatStateSnapshotReader reader;

    @Test
    void DB_좌석_스냅샷만_트랜잭션_경계_안에서_읽는다() {
        List<SeatStateSnapshotRow> states = List.of(new SeatStateSnapshotRow(1L, 1L, SeatStatus.AVAILABLE));
        when(seatMapReadRepository.findSeatStatuses(10L)).thenReturn(states);

        assertThat(reader.read(10L)).isSameAs(states);
    }
}
