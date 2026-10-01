package com.ticket.booking.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.exception.SeatAlreadyHeldException;

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
        when(holdStore.isHeld(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> holdRegistry.createHold(1L, 1L, List.of(10L), Duration.ofMinutes(5), FIXED_NOW))
                .isInstanceOf(SeatAlreadyHeldException.class);
    }

    @Test
    void hold를_생성하면_snapshot을_저장하고_반환한다() {
        Duration ttl = Duration.ofMinutes(5);

        Hold hold = holdRegistry.createHold(7L, 1L, List.of(10L, 20L), ttl, FIXED_NOW);

        assertThat(hold.holdKey()).startsWith("HOLD-");
        assertThat(hold.memberId()).isEqualTo(7L);
        assertThat(hold.performanceId()).isEqualTo(1L);
        assertThat(hold.seatIds()).containsExactly(10L, 20L);
        assertThat(hold.expiresAt()).isEqualTo(FIXED_NOW.plus(ttl));
        verify(holdStore).save(hold, ttl);
    }

    /** 없어진 {@code HoldKeyGeneratorTest}가 고정하던 hold 키 형식이다. */
    @Test
    void hold키는_HOLD_접두사와_하이픈없는_uuid로_생성한다() {
        Hold hold = holdRegistry.createHold(7L, 1L, List.of(10L), Duration.ofMinutes(5), FIXED_NOW);

        assertThat(hold.holdKey()).startsWith("HOLD-");
        assertThat(hold.holdKey().substring("HOLD-".length())).hasSize(32).doesNotContain("-");
    }

    @Test
    void hold키를_두번_생성하면_서로_다르다() {
        List<Long> seatIds = List.of(10L);
        Duration ttl = Duration.ofMinutes(5);

        Hold first = holdRegistry.createHold(7L, 1L, seatIds, ttl, FIXED_NOW);
        Hold second = holdRegistry.createHold(7L, 1L, seatIds, ttl, FIXED_NOW);

        assertThat(first.holdKey()).isNotEqualTo(second.holdKey());
    }

    @Test
    void release는_좌석_정규화를_store에_위임한다() {
        when(holdStore.release(1L, "hold-key", List.of(20L, 10L, 10L))).thenReturn(List.of(10L));

        List<Long> releasedSeatIds = holdRegistry.release(1L, "hold-key", List.of(20L, 10L, 10L));

        verify(holdStore).release(1L, "hold-key", List.of(20L, 10L, 10L));
        assertThat(releasedSeatIds).containsExactly(10L);
    }

    @Test
    void 현재_hold중인_좌석아이디를_조회한다() {
        when(holdStore.getHoldingSeatIds(1L)).thenReturn(Set.of(10L, 30L));

        Set<Long> result = holdRegistry.getHoldingSeatIds(1L);

        assertThat(result).containsExactlyInAnyOrder(10L, 30L);
    }

    @Test
    void isHeld는_hold여부를_반환한다() {
        when(holdStore.isHeld(1L, 10L)).thenReturn(true);

        boolean result = holdRegistry.isHeld(1L, 10L);

        assertThat(result).isTrue();
    }
}
