package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.ShowListReadRepository;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공연 검색 결과 개수 조회 UseCase
 * - 필터 선택 시 실제 데이터 없이 개수만 반환
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CountSearchShowsUseCase {
    private final ShowListReadRepository showListReadRepository;

    public record Input(ShowSearchCriteria request) {
    }

    public record Output(long count) {
    }

    public Output execute(final Input input) {
        return new Output(showListReadRepository.countSearchShows(input.request));
    }
}
