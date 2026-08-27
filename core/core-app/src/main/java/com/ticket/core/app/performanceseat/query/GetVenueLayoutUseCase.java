package com.ticket.core.app.performanceseat.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.repository.ShowRepository;
import com.ticket.core.domain.show.venue.Venue;
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
        Show show = showRepository.getById(input.showId());

        Venue venue = show.getVenue();
        if (venue == null) {
            throw new CoreException(ApplicationErrorType.DATA_NOT_FOUND,
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
