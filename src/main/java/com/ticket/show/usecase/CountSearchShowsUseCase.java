package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requireProvided;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

/** 공연 검색 결과 개수 조회 UseCase - 필터 선택 시 실제 데이터 없이 개수만 반환 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CountSearchShowsUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookupApi;

    public record Input(ShowSearchCriteria criteria) {
        public Input {
            criteria = requireProvided(criteria, "request");
        }
    }

    public record Output(long count) {}

    public Output execute(final Input input) {
        return new Output(showQuerydslRepository.countSearchShows(
                input.criteria(),
                ShowListParam.venueIdsIn(venueLookupApi, input.criteria().region())));
    }
}
