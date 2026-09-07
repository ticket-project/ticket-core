package com.ticket.venue.application.publicapi;

import com.ticket.venue.Region;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import com.ticket.venue.application.venue.query.VenueSummaryReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * {@link VenueLookup}의 venue 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueLookupService implements VenueLookup {

    private final VenueSummaryReadRepository venueSummaryReadRepository;

    @Override
    public Optional<VenueSummary> findSummary(final long venueId) {
        return venueSummaryReadRepository.findSummary(venueId);
    }

    @Override
    public Map<Long, VenueSummary> getSummaries(final Set<Long> venueIds) {
        return venueSummaryReadRepository.findSummaries(venueIds);
    }

    @Override
    public Set<Long> findIdsByRegion(final Region region) {
        Objects.requireNonNull(region, "region must not be null");
        return venueSummaryReadRepository.findIdsByRegion(region);
    }
}
