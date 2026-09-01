package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.ShowSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetLatestShowsUseCase {
    public static final int LATEST_SHOWS_MAX_COUNT = 10;
    private final ShowListReadRepository showListReadRepository;

    public record Input(String category) {
    }

    public record Output(List<ShowSummaryView> shows) {
    }

    public Output execute(final Input input) {
        return new Output(showListReadRepository.findLatestShows(input.category(), LATEST_SHOWS_MAX_COUNT));
    }
}
