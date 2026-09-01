package com.ticket.core.domain.hold.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.hold.store.HoldStore;
import com.ticket.core.domain.order.command.create.RequestedSeatIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class HoldManagerTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);

    @Mock
    private HoldStore holdStore;

    @Mock
    private HoldKeyGenerator holdKeyGenerator;

    private HoldManager holdManager;

    @BeforeEach
    void setUp() {
        this.holdManager = new HoldManager(holdStore, holdKeyGenerator);
    }

    @Test
    void createHold는_requestedSeatIds를_직접_받는다() throws NoSuchMethodException {
        Method method = HoldManager.class.getDeclaredMethod(
                "createHold",
                Long.class,
                Long.class,
                RequestedSeatIds.class,
                Duration.class,
                LocalDateTime.class
        );

        assertThat(method.getParameterTypes()[2]).isEqualTo(RequestedSeatIds.class);
    }

    @Test
    void 이미_hold된_좌석이_있으면_seatAlreadyHold예외를_던진다() {
        when(holdKeyGenerator.generate()).thenReturn("hold-key");
        when(holdStore.isHeld(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> holdManager.createHold(1L, 1L, RequestedSeatIds.from(List.of(10L)), Duration.ofMinutes(5), FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.SEAT_ALREADY_HOLD));
    }

    @Test
    void hold를_생성하면_snapshot을_저장하고_반환한다() {
        Duration ttl = Duration.ofMinutes(5);

        when(holdKeyGenerator.generate()).thenReturn("hold-key");

        Hold hold = holdManager.createHold(7L, 1L, RequestedSeatIds.from(List.of(10L, 20L)), ttl, FIXED_NOW);

        assertThat(hold.holdKey()).isEqualTo("hold-key");
        assertThat(hold.memberId()).isEqualTo(7L);
        assertThat(hold.performanceId()).isEqualTo(1L);
        assertThat(hold.seatIds()).containsExactly(10L, 20L);
        assertThat(hold.expiresAt()).isEqualTo(FIXED_NOW.plus(ttl));
        verify(holdStore).save(hold, ttl);
    }

    @Test
    void release는_중복좌석을_정렬해_전달한다() {
        when(holdStore.release(1L, "hold-key", List.of(10L, 20L))).thenReturn(List.of(10L));

        List<Long> releasedSeatIds = holdManager.release(1L, "hold-key", List.of(20L, 10L, 10L));

        verify(holdStore).release(1L, "hold-key", List.of(10L, 20L));
        assertThat(releasedSeatIds).containsExactly(10L);
    }

    @Test
    void 현재_hold중인_좌석아이디를_조회한다() {
        when(holdStore.getHoldingSeatIds(1L)).thenReturn(Set.of(10L, 30L));

        Set<Long> result = holdManager.getHoldingSeatIds(1L);

        assertThat(result).containsExactlyInAnyOrder(10L, 30L);
    }

    @Test
    void isHeld는_hold여부를_반환한다() {
        when(holdStore.isHeld(1L, 10L)).thenReturn(true);

        boolean result = holdManager.isHeld(1L, 10L);

        assertThat(result).isTrue();
    }
}
