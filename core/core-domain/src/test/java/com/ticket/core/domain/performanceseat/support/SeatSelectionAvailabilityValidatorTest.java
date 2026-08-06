package com.ticket.core.domain.performanceseat.support;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.query.SeatSelectionAvailabilityQueryRepository;
import com.ticket.core.domain.performanceseat.query.model.SeatSelectionAvailabilityView;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionAvailabilityValidatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Mock
    private HoldManager holdManager;

    @Mock
    private SeatSelectionAvailabilityQueryRepository queryRepository;

    @InjectMocks
    private SeatSelectionAvailabilityValidator validator;

    @Test
    void 예매가능시간의_가용좌석이면_통과한다() {
        when(queryRepository.findForSelection(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(false);

        assertThatCode(() -> validator.validate(10L, 20L, NOW)).doesNotThrowAnyException();
    }

    @Test
    void 회차가_없으면_NOT_FOUND를_던진다() {
        when(queryRepository.findForSelection(10L, 20L)).thenReturn(Optional.empty());

        assertError(ErrorType.NOT_FOUND_DATA);
    }

    @Test
    void 예매오픈_전이면_좌석상태보다_시간을_먼저_검증한다() {
        when(queryRepository.findForSelection(10L, 20L)).thenReturn(Optional.of(
                new SeatSelectionAvailabilityView(NOW.plusMinutes(1), NOW.plusHours(1), null, null)
        ));

        assertError(ErrorType.NOT_YET_RESERVE_TIME);
    }

    @Test
    void 예매마감_후면_실패한다() {
        when(queryRepository.findForSelection(10L, 20L)).thenReturn(Optional.of(
                new SeatSelectionAvailabilityView(NOW.minusHours(1), NOW.minusMinutes(1), 30L, PerformanceSeatState.AVAILABLE)
        ));

        assertError(ErrorType.PERFORMANCE_IS_PAST);
    }

    @Test
    void 회차에_없는_좌석이면_실패한다() {
        when(queryRepository.findForSelection(10L, 20L)).thenReturn(Optional.of(
                new SeatSelectionAvailabilityView(NOW.minusHours(1), NOW.plusHours(1), null, null)
        ));

        assertError(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE);
    }

    @Test
    void DB상_가용좌석이_아니면_실패한다() {
        when(queryRepository.findForSelection(10L, 20L)).thenReturn(Optional.of(
                new SeatSelectionAvailabilityView(NOW.minusHours(1), NOW.plusHours(1), 30L, PerformanceSeatState.RESERVED)
        ));

        assertError(ErrorType.NOT_EXIST_AVAILABLE_SEAT);
    }

    @Test
    void 이미_홀드된_좌석이면_실패한다() {
        when(queryRepository.findForSelection(10L, 20L)).thenReturn(Optional.of(available()));
        when(holdManager.isHeld(10L, 20L)).thenReturn(true);

        assertError(ErrorType.SEAT_ALREADY_HOLD);
    }

    private SeatSelectionAvailabilityView available() {
        return new SeatSelectionAvailabilityView(
                NOW.minusHours(1),
                NOW.plusHours(1),
                30L,
                PerformanceSeatState.AVAILABLE
        );
    }

    private void assertError(final ErrorType expected) {
        assertThatThrownBy(() -> validator.validate(10L, 20L, NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(expected));
    }
}
