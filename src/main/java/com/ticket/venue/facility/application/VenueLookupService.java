package com.ticket.venue.facility.application;

import com.ticket.venue.facility.application.port.VenueSummaryQueryPort;

import com.ticket.venue.Region;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import com.ticket.venue.facility.application.port.VenueSummaryQueryPort;
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

    private final VenueSummaryQueryPort venueSummaryQueryPort;

    @Override
    public Optional<VenueSummary> findSummary(final long venueId) {
        return venueSummaryQueryPort.findSummary(venueId);
    }

    @Override
    public Map<Long, VenueSummary> getSummaries(final Set<Long> venueIds) {
        return venueSummaryQueryPort.findSummaries(venueIds);
    }

    @Override
    public Set<Long> findIdsByRegion(final Region region) {
        Objects.requireNonNull(region, "region must not be null");
        return venueSummaryQueryPort.findIdsByRegion(region);
    }
}
