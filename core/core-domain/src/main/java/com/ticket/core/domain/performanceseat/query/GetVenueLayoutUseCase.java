package com.ticket.core.domain.performanceseat.query;

import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.query.ShowFinder;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetVenueLayoutUseCase {

    private final ShowFinder showFinder;

    public record Input(Long showId) {}
    public record Output(String name,
                         int viewBoxWidth,
                         int viewBoxHeight,
                         double seatDiameter) {}

    public Output execute(Input input) {
        Show show = showFinder.findById(input.showId());

        Venue venue = show.getVenue();
        if (venue == null) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA,
                    "공연에 연결된 공연장을 찾을 수 없습니다.");
        }

        return new Output(
                venue.getName(),
                venue.getViewBoxWidth(),
                venue.getViewBoxHeight(),
                venue.getSeatDiameter()
        );
    }
}
