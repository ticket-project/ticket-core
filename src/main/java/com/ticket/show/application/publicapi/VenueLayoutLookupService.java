package com.ticket.show.application.publicapi;

import com.ticket.show.VenueLayout;
import com.ticket.show.VenueLayoutLookup;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.Venue;
import com.ticket.show.domain.show.repository.ShowRepository;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link VenueLayoutLookup}의 show 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueLayoutLookupService implements VenueLayoutLookup {

    private final ShowRepository showRepository;

    @Override
    public VenueLayout getVenueLayout(final long showId) {
        final Show show = showRepository.findById(showId)
                .orElseThrow(() -> new NotFoundException(
                        "공연을 찾을 수 없습니다. id=" + showId));
        final Venue venue = show.getVenue();
        if (venue == null) {
            throw new NotFoundException("공연에 연결된 공연장을 찾을 수 없습니다.");
        }
        return new VenueLayout(venue.getName(), venue.getViewBoxWidth(), venue.getViewBoxHeight(), venue.getSeatDiameter());
    }
}
