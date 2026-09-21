package com.ticket.show.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleOpeningSoonShowsUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookup;
    private final ShowCardImagePathConverter showCardImagePathConverter;

    public record Input(String category, int size) {
        public Input {
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(List<Item> shows) {}

    /**
     * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code saleStartDate}는 그대로 고정한다.
     */
    public record Item(
            Long id,
            @Nullable String title,
            @Nullable String image,
            @Nullable String venue,
            @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt) {}

    public Output execute(final Input input) {
        final List<Show> shows =
                showQuerydslRepository.findSaleOpeningSoonSummaries(input.category(), input.size());
        final VenueDisplays venues =
                VenueDisplays.load(venueLookup, shows.stream().map(Show::getVenueId).toList());
        return new Output(shows.stream().map(show -> toItem(show, venues)).toList());
    }

    private Item toItem(final Show show, final VenueDisplays venues) {
        return new Item(
                show.getId(),
                show.getTitle(),
                showCardImagePathConverter.toCardImage(show.getImage()),
                venues.nameOf(show.getVenueId()),
                show.getDisplaySaleStartsAt());
    }
}
