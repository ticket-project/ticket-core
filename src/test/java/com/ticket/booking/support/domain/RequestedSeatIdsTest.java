package com.ticket.booking.support.domain;

import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class RequestedSeatIdsTest {

    @Test
    void 빈_좌석_id_목록이면_예외를_던진다() {
        assertThatThrownBy(() -> RequestedSeatIds.from(List.of()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void null_좌석_id_목록이면_예외를_던진다() {
        assertThatThrownBy(() -> RequestedSeatIds.from(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 중복된_좌석_id가_있으면_예외를_던진다() {
        assertThatThrownBy(() -> RequestedSeatIds.from(List.of(3L, 1L, 3L)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void null_좌석_id가_포함되면_예외를_던진다() {
        final List<Long> seatIds = Arrays.asList(3L, null, 5L);

        assertThatThrownBy(() -> RequestedSeatIds.from(seatIds))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 좌석_id를_오름차순으로_정렬한다() {
        final RequestedSeatIds seatIds = RequestedSeatIds.from(List.of(7L, 3L, 5L));

        assertThat(seatIds.toList()).containsExactly(3L, 5L, 7L);
    }

    @Test
    void values_대신_toList를_노출한다() {
        assertThat(methodNames()).contains("toList");
        assertThat(methodNames()).doesNotContain("values");
    }

    private List<String> methodNames() {
        return Arrays.stream(RequestedSeatIds.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
    }
}
