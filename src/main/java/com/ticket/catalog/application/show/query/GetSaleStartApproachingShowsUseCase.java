package com.ticket.catalog.application.show.query;

import com.ticket.catalog.application.show.query.model.ShowOpeningSoonSummaryView;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleStartApproachingShowsUseCase {
    private final ShowListReadRepository showListReadRepository;

    public record Input(String category, int size) {
        public Input {
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(List<ShowOpeningSoonSummaryView> shows) {
    }

    public Output execute(final Input input) {
        return new Output(showListReadRepository.findShowsSaleOpeningSoon(input.category(), input.size()));
    }
}
