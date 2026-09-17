package com.ticket.venue.usecase;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;
import com.ticket.venue.query.VenueSummaryQueryPort;

import lombok.RequiredArgsConstructor;

/** {@link VenueLookupApi}의 venue 소유 구현이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueLookupService implements VenueLookupApi {
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
