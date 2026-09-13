package com.ticket.booking.selection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class DeselectedSeatIdsTest {
    @Test
    void 입력_컬렉션을_불변_리스트로_보관한다() {
        List<Long> source = new ArrayList<>(List.of(20L, 21L));

        DeselectedSeatIds seatIds = DeselectedSeatIds.from(source);
        source.add(22L);

        assertThat(seatIds.values()).containsExactly(20L, 21L);
    }

    @Test
    void 각_좌석에_대해_동작을_위임한다() {
        List<Long> collected = new ArrayList<>();

        DeselectedSeatIds.from(List.of(20L, 21L)).forEach(collected::add);

        assertThat(collected).containsExactly(20L, 21L);
    }
}
