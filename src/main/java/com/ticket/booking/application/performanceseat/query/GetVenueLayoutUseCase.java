package com.ticket.booking.application.performanceseat.query;

import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.VenueLayout;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetVenueLayoutUseCase {

    private final ShowLookup showLookup;

    public record Input(Long showId) {
        public Input {
            if (showId == null) {
                throw new InvalidRequestException("showId는 필수입니다.");
            }
            if (showId <= 0) {
                throw new InvalidRequestException("showId는 양수여야 합니다.");
            }
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
