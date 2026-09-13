package com.ticket.booking.selection.application.usecase;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.selection.application.SeatSelectionCoordinator;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class DeselectSeatUseCaseTest {
    @Mock private SeatSelectionCoordinator seatSelectionCoordinator;
    @InjectMocks private DeselectSeatUseCase useCase;

    /**
     * 해제 여부 판단과 발행은 coordinator가 좌석 락 안에서 한다. 이 use case는 입력 검증과 위임만 한다 — 실제 해제 여부와 무관하게 여기서 발행하던 옛
     * 경로는 {@code SeatSelectionCoordinatorTest}가 고정한다.
     */
    @Test
    void 좌석_락_안에서_해제하도록_coordinator에_위임한다() {
        useCase.execute(new DeselectSeatUseCase.Input(10L, 20L, 1L));

        verify(seatSelectionCoordinator).deselect(10L, 20L, 1L);
    }
}
