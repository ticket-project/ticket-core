package com.ticket.show.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.show.domain.show.Show;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetLatestShowsUseCase {
    public static final int LATEST_SHOWS_MAX_COUNT = 10;
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookup;
    private final ShowCardImagePathConverter showCardImagePathConverter;

    public record Input(String category) {}

    public record Output(List<Item> shows) {}

    public record Item(
            Long id,
            @Nullable String title,
            @Nullable String image,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable String venue,
            LocalDateTime createdAt) {}

    public Output execute(final Input input) {
        final List<Show> shows = showQuerydslRepository.findLatestShows(input.category(), LATEST_SHOWS_MAX_COUNT);
        final Map<Long, VenueSnapshot> venuesById = venueLookup.getSummaries(
                Set.copyOf(shows.stream().map(Show::getVenueId).toList()));
        final VenueDisplays venues = new VenueDisplays(venuesById);
        return new Output(shows.stream().map(show -> toItem(show, venues)).toList());
    }

    private Item toItem(final Show show, final VenueDisplays venues) {
        return new Item(
                show.getId(),
                show.getTitle(),
                showCardImagePathConverter.toCardImage(show.getImage()),
                show.getStartDate(),
                show.getEndDate(),
                venues.nameOf(show.getVenueId()),
                show.getCreatedAt());
    }
}
