package com.ticket.booking.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class HoldRegistryTest {
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);

    @Mock
    private HoldStore holdStore;

    private HoldRegistry holdRegistry;

    @BeforeEach
    void setUp() {
        this.holdRegistry = new HoldRegistry(holdStore);
    }

    @Test
    void 이미_hold된_좌석이_있으면_seatAlreadyHold예외를_던진다() {
        when(holdStore.saveIfAbsent(any(Hold.class), eq(Duration.ofMinutes(5)))).thenReturn(false);

        assertThatThrownBy(() -> holdRegistry.createHold(1L, 1L, List.of(10L), Duration.ofMinutes(5), FIXED_NOW))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E6000);
    }

    @Test
    void hold를_생성하면_snapshot을_저장하고_반환한다() {
        Duration ttl = Duration.ofMinutes(5);
        when(holdStore.saveIfAbsent(any(Hold.class), eq(ttl))).thenReturn(true);

        Hold hold = holdRegistry.createHold(7L, 1L, List.of(10L, 20L), ttl, FIXED_NOW);

        assertThat(hold.holdKey()).startsWith("HOLD-");
        assertThat(hold.memberId()).isEqualTo(7L);
        assertThat(hold.performanceId()).isEqualTo(1L);
        assertThat(hold.seatIds()).containsExactly(10L, 20L);
        assertThat(hold.expiresAt()).isEqualTo(FIXED_NOW.plus(ttl));
        verify(holdStore).saveIfAbsent(hold, ttl);
    }

    /** 없어진 {@code HoldKeyGeneratorTest}가 고정하던 hold 키 형식이다. */
    @Test
    void hold키는_HOLD_접두사와_하이픈없는_uuid로_생성한다() {
        when(holdStore.saveIfAbsent(any(Hold.class), eq(Duration.ofMinutes(5)))).thenReturn(true);
        Hold hold = holdRegistry.createHold(7L, 1L, List.of(10L), Duration.ofMinutes(5), FIXED_NOW);

        assertThat(hold.holdKey()).startsWith("HOLD-");
        assertThat(hold.holdKey().substring("HOLD-".length())).hasSize(32).doesNotContain("-");
    }

    @Test
    void hold키를_두번_생성하면_서로_다르다() {
        List<Long> seatIds = List.of(10L);
        Duration ttl = Duration.ofMinutes(5);
        when(holdStore.saveIfAbsent(any(Hold.class), eq(ttl))).thenReturn(true);

        Hold first = holdRegistry.createHold(7L, 1L, seatIds, ttl, FIXED_NOW);
        Hold second = holdRegistry.createHold(7L, 1L, seatIds, ttl, FIXED_NOW);

        assertThat(first.holdKey()).isNotEqualTo(second.holdKey());
    }
}
