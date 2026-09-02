package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.repository.ShowRepository;
import com.ticket.catalog.internal.domain.show.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetVenueLayoutUseCase {

    private final ShowRepository showRepository;

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
        Show show = showRepository.findById(input.showId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + input.showId()));

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
