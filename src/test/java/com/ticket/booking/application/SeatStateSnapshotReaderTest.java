package com.ticket.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.application.port.SeatStateQueryPort;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatStateSnapshotReaderTest {
    @Mock private SeatStateQueryPort seatStateQueryPort;
    @InjectMocks private SeatStateSnapshotReader reader;

    @Test
    void DB_좌석_스냅샷만_트랜잭션_경계_안에서_읽는다() {
        List<SeatStateSnapshotRow> states =
                List.of(new SeatStateSnapshotRow(1L, 1L, SeatStatus.AVAILABLE));
        when(seatStateQueryPort.findSeatStates(10L)).thenReturn(states);

        assertThat(reader.read(10L)).isSameAs(states);
    }
}
