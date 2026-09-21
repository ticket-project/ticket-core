package com.ticket.venue.persistence;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatAddress;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSummary;

import lombok.RequiredArgsConstructor;

/**
 * {@link VenueLookupApi}와 {@link VenueSeatLookupApi}의 venue 소유 구현이다.
 *
 * <p>venue 표시값 조회도 물리 좌석 조회도 venue 자기 DB 한 번으로 끝나므로 공개 계약을 이 조회가 직접 구현한다 — 사이에 위임만 하는 service를 두면
 * 계약과 SQL 사이에 읽을 것 없는 경유 지점이 하나 늘 뿐이다. 다른 module은 계속 {@code venue.api}의 interface만 본다.
 *
 * <p>SQL 자체는 {@code SpringData*JpaRepository}의 생성자 표현식이 갖는다. 여기 남는 것은 그것으로 표현되지 않는 것뿐이다 — 인자 검증, 빈
 * 집합일 때 질의를 아끼는 가드, 배치 조회 결과를 {@code Map}으로 접는 조립.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueQueryRepository implements VenueLookupApi, VenueSeatLookupApi {
    private final SpringDataVenueJpaRepository venueJpaRepository;
    private final SpringDataSeatJpaRepository seatJpaRepository;

    @Override
    public Optional<VenueSummary> findSummary(final long venueId) {
        return venueJpaRepository.findSummaryById(venueId);
    }

    @Override
    public Map<Long, VenueSummary> getSummaries(final Set<Long> venueIds) {
        if (venueIds.isEmpty()) {
            return Map.of();
        }
        return venueJpaRepository.findSummariesByIdIn(venueIds).stream()
                .collect(Collectors.toMap(VenueSummary::venueId, s -> s));
    }

    @Override
    public Set<Long> findIdsByRegion(final Region region) {
        Objects.requireNonNull(region, "region must not be null");
        return Set.copyOf(venueJpaRepository.findIdsByRegion(region));
    }

    @Override
    public List<VenueSeatAddress> findSeatAddresses(final long venueId, final Set<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return seatJpaRepository.findAddressesByVenueIdAndIdIn(venueId, seatIds);
    }

    @Override
    public List<VenueSeatLayout> findAllSeatLayouts(final long venueId) {
        return seatJpaRepository.findLayoutsByVenueId(venueId);
    }
}
