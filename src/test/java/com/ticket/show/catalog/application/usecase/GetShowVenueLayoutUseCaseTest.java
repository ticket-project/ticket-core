package com.ticket.show.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.catalog.domain.Show;
import com.ticket.show.catalog.domain.ShowRepository;
import com.ticket.venue.Region;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowVenueLayoutUseCaseTest {
    @Mock private ShowRepository showRepository;
    @Mock private VenueLookup venueLookup;
    @InjectMocks private GetShowVenueLayoutUseCase useCase;

    @Test
    void 공연장_레이아웃을_반환한다() {
        // given
        Show show = mock(Show.class);
        when(show.getVenueId()).thenReturn(200L);
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));
        when(venueLookup.findSummary(200L))
                .thenReturn(
                        Optional.of(
                                new VenueSummary(
                                        200L,
                                        "올림픽홀",
                                        "주소",
                                        Region.SEOUL,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        "02-0000-0000",
                                        "image",
                                        new VenueSummary.SeatMapLayout(1000, 800, 12.0))));
        // when
        GetShowVenueLayoutUseCase.Output output =
                useCase.execute(new GetShowVenueLayoutUseCase.Input(100L));
        // then
        assertThat(output.name()).isEqualTo("올림픽홀");
        assertThat(output.viewBoxWidth()).isEqualTo(1000);
        assertThat(output.viewBoxHeight()).isEqualTo(800);
        assertThat(output.seatDiameter()).isEqualTo(12.0);
    }

    @Test
    void 공연이_없으면_예외를_던진다() {
        when(showRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetShowVenueLayoutUseCase.Input(100L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 공연장_정보가_없으면_예외를_던진다() {
        Show show = mock(Show.class);
        when(show.getVenueId()).thenReturn(null);
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));

        assertThatThrownBy(() -> useCase.execute(new GetShowVenueLayoutUseCase.Input(100L)))
                .isInstanceOf(NotFoundException.class);
    }
}
