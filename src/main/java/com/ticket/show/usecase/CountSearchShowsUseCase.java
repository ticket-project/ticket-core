package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requireProvided;

import java.util.Set;

import org.jspecify.annotations.Nullable;
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
    private final VenueLookupApi venueLookup;

    public record Input(ShowSearchCriteria criteria) {
        public Input {
            criteria = requireProvided(criteria, "request");
        }
    }

    public record Output(long count) {}

    public Output execute(final Input input) {
        return new Output(showQuerydslRepository.countSearchShows(
                input.criteria(), venueIdsOf(input.criteria().getRegion())));
    }

    /**
     * 지역 조건을 venueId 집합으로 해석한다.
     *
     * <p><b>"지역 없음"과 "지역은 있으나 그 지역에 공연장이 없음"은 다른 결과다.</b> 그래서 {@code null}(지역 조건 자체가 없음)과 빈 집합(조건은 있는데 해당 공연장이 없으니 결과
     * 0건)을 구분해 넘긴다. 이 둘을 뭉개면 "제주에 공연장이 하나도 없다"가 "전체 목록"으로 조용히 바뀐다.
     */
    private @Nullable Set<Long> venueIdsOf(final @Nullable String region) {
        return region == null ? null : venueLookup.findIdsByRegion(region);
    }
}
