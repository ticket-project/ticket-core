package com.ticket.venue.seat.application;

import com.ticket.venue.seat.application.port.VenueSeatQueryPort;

import com.ticket.venue.VenueSeatAddress;
import com.ticket.venue.VenueSeatLayout;
import com.ticket.venue.VenueSeatLookup;
import com.ticket.venue.seat.application.port.VenueSeatQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * {@link VenueSeatLookup}의 venue 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueSeatLookupService implements VenueSeatLookup {

    private final VenueSeatQueryPort venueSeatQueryPort;

    @Override
    public List<VenueSeatAddress> findSeatAddresses(final long venueId, final Set<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return List.of();
        }
        return venueSeatQueryPort.findSeatAddresses(venueId, seatIds);
    }

    @Override
    public List<VenueSeatLayout> findAllSeatLayouts(final long venueId) {
        return venueSeatQueryPort.findAllSeatLayouts(venueId);
    }
}
