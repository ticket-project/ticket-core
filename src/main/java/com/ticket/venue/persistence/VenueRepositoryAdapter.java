package com.ticket.venue.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.venue.domain.Region;
import com.ticket.venue.domain.Venue;
import com.ticket.venue.domain.VenueRepository;

import lombok.RequiredArgsConstructor;

/** {@link VenueRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {
    private final SpringDataVenueJpaRepository jpaRepository;

    @Override
    public Optional<Venue> findById(final Long venueId) {
        return jpaRepository.findById(venueId);
    }

    @Override
    public List<Venue> findAllById(final Collection<Long> venueIds) {
        return jpaRepository.findAllById(venueIds);
    }

    @Override
    public List<Long> findIdsByRegion(final Region region) {
        return jpaRepository.findIdsByRegion(region);
    }
}
