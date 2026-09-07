package com.ticket.venue.infrastructure.venue.query;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.venue.Region;
import com.ticket.venue.VenueSummary;
import com.ticket.venue.application.venue.query.VenueSummaryReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ticket.venue.domain.venue.QVenue.venue;

@Repository
@RequiredArgsConstructor
public class QuerydslVenueSummaryReadRepository implements VenueSummaryReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<VenueSummary> findSummary(final long venueId) {
        return Optional.ofNullable(queryFactory
                .select(row())
                .from(venue)
                .where(venue.id.eq(venueId))
                .fetchOne())
                .map(this::toSummary);
    }

    @Override
    public Map<Long, VenueSummary> findSummaries(final Set<Long> venueIds) {
        if (venueIds.isEmpty()) {
            return Map.of();
        }
        final List<Tuple> rows = queryFactory
                .select(row())
                .from(venue)
                .where(venue.id.in(venueIds))
                .fetch();
        return rows.stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(VenueSummary::venueId, s -> s));
    }

    @Override
    public Set<Long> findIdsByRegion(final Region region) {
        return Set.copyOf(queryFactory
                .select(venue.id)
                .from(venue)
                .where(venue.region.eq(region))
                .fetch());
    }

    private com.querydsl.core.types.Expression<?>[] row() {
        return new com.querydsl.core.types.Expression<?>[] {
                venue.id, venue.name, venue.address, venue.region, venue.latitude, venue.longitude,
                venue.phone, venue.imageUrl, venue.viewBoxWidth, venue.viewBoxHeight, venue.seatDiameter
        };
    }

    private VenueSummary toSummary(final Tuple tuple) {
        return new VenueSummary(
                tuple.get(venue.id),
                tuple.get(venue.name),
                tuple.get(venue.address),
                tuple.get(venue.region),
                tuple.get(venue.latitude),
                tuple.get(venue.longitude),
                tuple.get(venue.phone),
                tuple.get(venue.imageUrl),
                new VenueSummary.SeatMapLayout(
                        tuple.get(venue.viewBoxWidth),
                        tuple.get(venue.viewBoxHeight),
                        tuple.get(venue.seatDiameter)
                )
        );
    }
}
