package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.ShowOpeningSoonSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleStartApproachingShowsUseCase {
    private final ShowListQueryRepository showListQueryRepository;

    public record Input(String category, int size) {
    }

    public record Output(List<ShowOpeningSoonSummaryView> shows) {
    }

    public Output execute(final Input input) {
        return new Output(showListQueryRepository.findShowsSaleOpeningSoon(input.category(), input.size()));
    }
}
