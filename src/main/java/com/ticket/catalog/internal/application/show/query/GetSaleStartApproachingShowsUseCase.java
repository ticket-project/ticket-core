package com.ticket.catalog.internal.application.show.query;

import com.ticket.catalog.internal.application.show.query.model.ShowOpeningSoonSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.ticket.shared.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleStartApproachingShowsUseCase {
    private final ShowListReadRepository showListReadRepository;

    public record Input(String category, int size) {
        public Input {
            RequiredInput.positiveSize(size, "size");
        }
    }

    public record Output(List<ShowOpeningSoonSummaryView> shows) {
    }

    public Output execute(final Input input) {
        return new Output(showListReadRepository.findShowsSaleOpeningSoon(input.category(), input.size()));
    }
}
