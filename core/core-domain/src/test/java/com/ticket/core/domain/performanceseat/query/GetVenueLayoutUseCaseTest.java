package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.query.ShowFinder;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetVenueLayoutUseCaseTest {

    @Mock
    private ShowFinder showFinder;

    @InjectMocks
    private GetVenueLayoutUseCase useCase;

    @Test
    void 공연장_레이아웃을_반환한다() {
        //given
        Show show = mock(Show.class);
        Venue venue = mock(Venue.class);
        when(showFinder.findById(100L)).thenReturn(show);
        when(show.getVenue()).thenReturn(venue);
        when(venue.getName()).thenReturn("올림픽홀");
        when(venue.getViewBoxWidth()).thenReturn(1000);
        when(venue.getViewBoxHeight()).thenReturn(800);
        when(venue.getSeatDiameter()).thenReturn(12.0);

        //when
        GetVenueLayoutUseCase.Output output = useCase.execute(new GetVenueLayoutUseCase.Input(100L));

        //then
        assertThat(output.name()).isEqualTo("올림픽홀");
        assertThat(output.viewBoxWidth()).isEqualTo(1000);
    }

    @Test
    void 공연장_정보가_없으면_예외를_던진다() {
        //given
        Show show = mock(Show.class);
        when(showFinder.findById(100L)).thenReturn(show);
        when(show.getVenue()).thenReturn(null);

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new GetVenueLayoutUseCase.Input(100L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }
}
