package com.ticket.show.usecase;

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
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;
import com.ticket.venue.exception.VenueNotFoundException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowVenueLayoutUseCaseTest {
    @Mock
    private ShowRepository showRepository;

    @Mock
    private VenueLookupApi venueLookupApi;

    @InjectMocks
    private GetShowVenueLayoutUseCase useCase;

    @Test
    void 공연장_레이아웃을_반환한다() {
        // given
        Show show = mock(Show.class);
        when(show.getVenueId()).thenReturn(200L);
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));
        when(venueLookupApi.getVenueSnapshot(200L))
                .thenReturn(new VenueSnapshot(
                        200L,
                        "올림픽홀",
                        "주소",
                        new VenueSnapshot.RegionView("SEOUL", "서울"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "02-0000-0000",
                        "image",
                        new VenueSnapshot.SeatMapLayout(1000, 800, 12.0)));
        // when
        GetShowVenueLayoutUseCase.Output output = useCase.execute(new GetShowVenueLayoutUseCase.Input(100L));
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

    /** venueId는 있는데 venue module에 그 공연장이 없는 dangling 상태다. 좌석 맵은 공연장이 필수라 venue가 던지는 not-found가 그대로 나간다. */
    @Test
    void dangling_venueId는_venue의_not_found가_전파된다() {
        Show show = mock(Show.class);
        when(show.getVenueId()).thenReturn(200L);
        when(showRepository.findById(100L)).thenReturn(Optional.of(show));
        when(venueLookupApi.getVenueSnapshot(200L)).thenThrow(new VenueNotFoundException(200L));

        assertThatThrownBy(() -> useCase.execute(new GetShowVenueLayoutUseCase.Input(100L)))
                .isInstanceOf(VenueNotFoundException.class)
                .isInstanceOf(NotFoundException.class);
    }
}
