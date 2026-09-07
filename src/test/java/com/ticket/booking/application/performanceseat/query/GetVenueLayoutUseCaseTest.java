package com.ticket.booking.application.performanceseat.query;

import com.ticket.show.ShowLookup;
import com.ticket.show.VenueLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetVenueLayoutUseCaseTest {

    @Mock
    private ShowLookup showLookup;

    @InjectMocks
    private GetVenueLayoutUseCase useCase;

    @Test
    void 공연장_레이아웃을_반환한다() {
        //given
        when(showLookup.getVenueLayout(100L)).thenReturn(new VenueLayout("올림픽홀", 1000, 800, 12.0));

        //when
        GetVenueLayoutUseCase.Output output = useCase.execute(new GetVenueLayoutUseCase.Input(100L));

        //then
        assertThat(output.name()).isEqualTo("올림픽홀");
        assertThat(output.viewBoxWidth()).isEqualTo(1000);
        assertThat(output.viewBoxHeight()).isEqualTo(800);
        assertThat(output.seatDiameter()).isEqualTo(12.0);
    }
}
