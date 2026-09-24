package com.ticket.venue.usecase;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;
import com.ticket.venue.domain.Region;
import com.ticket.venue.domain.Venue;
import com.ticket.venue.domain.VenueRepository;
import com.ticket.venue.exception.VenueNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * {@link VenueLookupApi}의 venue 소유 구현이다.
 *
 * <p>조회는 엔티티를 돌려주고 공개 계약 타입으로의 변환은 여기서 한 번만 한다({@code docs/coding-guidelines.md} §10-1). 다른 module은 계속
 * {@code venue.api}의 interface만 본다 — venue JPA entity를 노출하지 않는다.
 *
 * <p>물리 좌석은 {@link SeatLookupService}가 따로 갖는다 — Seat은 Venue와 다른 Aggregate다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueLookupService implements VenueLookupApi {
    private final VenueRepository venueRepository;

    @Override
    public VenueSnapshot getVenueSnapshot(final long venueId) {
        return toSnapshot(venueRepository.findById(venueId).orElseThrow(() -> new VenueNotFoundException(venueId)));
    }

    @Override
    public Map<Long, VenueSnapshot> getSummaries(final Set<Long> venueIds) {
        if (venueIds.isEmpty()) {
            return Map.of();
        }
        return venueRepository.findAllById(venueIds).stream()
                .map(VenueLookupService::toSnapshot)
                .collect(Collectors.toMap(VenueSnapshot::venueId, summary -> summary));
    }

    @Override
    public Set<Long> findIdsByRegion(final String regionCode) {
        Objects.requireNonNull(regionCode, "regionCode must not be null");
        return Set.copyOf(venueRepository.findIdsByRegion(Region.from(regionCode)));
    }

    private static VenueSnapshot toSnapshot(final Venue venue) {
        return new VenueSnapshot(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                toRegionView(venue.getRegion()),
                venue.getLatitude(),
                venue.getLongitude(),
                venue.getPhone(),
                venue.getImageUrl(),
                new VenueSnapshot.SeatMapLayout(
                        venue.getViewBoxWidth(), venue.getViewBoxHeight(), venue.getSeatDiameter()));
    }

    private static VenueSnapshot.@Nullable RegionView toRegionView(final @Nullable Region region) {
        return region == null ? null : new VenueSnapshot.RegionView(region.name(), region.getDescription());
    }
}
