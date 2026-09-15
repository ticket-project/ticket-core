package com.ticket.show.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.application.RegionVenueIds;
import com.ticket.show.application.ShowSearchCriteria;
import com.ticket.show.application.port.ShowListQueryPort;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

/** 공연 검색 결과 개수 조회 UseCase - 필터 선택 시 실제 데이터 없이 개수만 반환 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CountSearchShowsUseCase {
    private final ShowListQueryPort showListQueryPort;
    private final VenueLookupApi venueLookup;

    public record Input(ShowSearchCriteria criteria) {
        public Input {
            if (criteria == null) {
                throw new InvalidRequestException("request는 필수입니다.");
            }
        }
    }

    public record Output(long count) {}

    public Output execute(final Input input) {
        return new Output(
                showListQueryPort.countSearchShows(
                        input.criteria(),
                        RegionVenueIds.resolve(venueLookup, input.criteria().getRegion())));
    }
}
