package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.VenueLayout;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ticket.shared.RequiredInput;

@Service
@RequiredArgsConstructor
public class GetVenueLayoutUseCase {

    private final ShowLookup showLookup;

    public record Input(Long showId) {
        public Input {
            RequiredInput.positiveId(showId, "showId");
        }
    }
    public record Output(String name,
                         int viewBoxWidth,
                         int viewBoxHeight,
                         double seatDiameter) {}

    public Output execute(Input input) {
        final VenueLayout layout = showLookup.getVenueLayout(input.showId());
        return new Output(
                layout.name(),
                layout.viewBoxWidth(),
                layout.viewBoxHeight(),
                layout.seatDiameter()
        );
    }
}
