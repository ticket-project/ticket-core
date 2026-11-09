package com.ticket.booking.seat.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

/** 좌석당 1비트 계약(seatId 오름차순, 앞 좌석이 높은 비트, 1 = 고를 수 있음, 남는 비트 0)을 고정한다. FE가 이 순서로 읽는다. */
@SuppressWarnings("NonAsciiCharacters")
class SeatAvailabilityBitsReaderTest {
    @Test
    void 앞_좌석이_높은_비트이고_못_고르는_좌석은_0이다() {
        final List<Long> seatIds = LongStream.rangeClosed(101, 108).boxed().toList();

        final byte[] bits = SeatAvailabilityBitsReader.availableBits(seatIds, Set.of(102L, 105L, 108L));

        assertThat(bits).containsExactly((byte) 0b1011_0110);
        assertThat(Base64.getEncoder().encodeToString(bits)).isEqualTo("tg==");
    }

    @Test
    void 마지막_바이트의_남는_비트는_0이다() {
        final List<Long> seatIds = LongStream.rangeClosed(1, 10).boxed().toList();

        final byte[] bits = SeatAvailabilityBitsReader.availableBits(seatIds, Set.of());

        assertThat(bits).containsExactly((byte) 0xFF, (byte) 0b1100_0000);
    }

    @Test
    void 좌석이_없으면_빈_배열이다() {
        assertThat(SeatAvailabilityBitsReader.availableBits(List.of(), Set.of()))
                .isEmpty();
    }
}
