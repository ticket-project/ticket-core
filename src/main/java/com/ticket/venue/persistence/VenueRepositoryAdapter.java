package com.ticket.venue.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;
import com.ticket.venue.domain.Venue;

import lombok.RequiredArgsConstructor;

/**
 * {@link VenueLookupApi}의 venue 소유 구현이다.
 *
 * <p>venue 표시값 조회는 venue 자기 DB 한 번으로 끝나므로 공개 계약을 이 adapter가 직접 구현한다 — 사이에 위임만 하는 service를 두면 계약과
 * SQL 사이에 읽을 것 없는 경유 지점이 하나 늘 뿐이다. 다른 module은 계속 {@code venue.api}의 interface만 본다.
 *
 * <p>조회는 엔티티를 받고 공개 계약 타입으로의 변환은 여기서 한 번만 한다({@code docs/readability-guidelines.md} §10-1). venue에는
 * use case 계층이 없으므로 계약 구현체인 이 adapter가 그 자리다.
 *
 * <p>물리 좌석은 {@link SeatRepositoryAdapter}가 따로 갖는다 — Seat은 Venue와 다른 Aggregate다.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueRepositoryAdapter implements VenueLookupApi {
    private final SpringDataVenueJpaRepository venueJpaRepository;

    @Override
    public Optional<VenueSummary> findSummary(final long venueId) {
        return venueJpaRepository.findById(venueId).map(VenueRepositoryAdapter::toSummary);
    }

    @Override
    public Map<Long, VenueSummary> getSummaries(final Set<Long> venueIds) {
        if (venueIds.isEmpty()) {
            return Map.of();
        }
        return venueJpaRepository.findAllById(venueIds).stream()
                .map(VenueRepositoryAdapter::toSummary)
                .collect(Collectors.toMap(VenueSummary::venueId, summary -> summary));
    }

    @Override
    public Set<Long> findIdsByRegion(final Region region) {
        Objects.requireNonNull(region, "region must not be null");
        return Set.copyOf(venueJpaRepository.findIdsByRegion(region));
    }

    private static VenueSummary toSummary(final Venue venue) {
        return new VenueSummary(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                venue.getRegion(),
                venue.getLatitude(),
                venue.getLongitude(),
                venue.getPhone(),
                venue.getImageUrl(),
                new VenueSummary.SeatMapLayout(
                        venue.getViewBoxWidth(),
                        venue.getViewBoxHeight(),
                        venue.getSeatDiameter()));
    }
}
