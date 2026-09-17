package com.ticket.venue.persistence;

import static com.ticket.venue.domain.QVenue.venue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueSummary;
import com.ticket.venue.query.VenueSummaryQueryPort;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuerydslVenueSummaryQueryAdapter implements VenueSummaryQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<VenueSummary> findSummary(final long venueId) {
        return Optional.ofNullable(
                        queryFactory
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
        final List<Tuple> rows =
                queryFactory.select(row()).from(venue).where(venue.id.in(venueIds)).fetch();
        return rows.stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(VenueSummary::venueId, s -> s));
    }

    @Override
    public Set<Long> findIdsByRegion(final Region region) {
        return Set.copyOf(
                queryFactory.select(venue.id).from(venue).where(venue.region.eq(region)).fetch());
    }

    private com.querydsl.core.types.Expression<?>[] row() {
        return new com.querydsl.core.types.Expression<?>[] {
            venue.id,
            venue.name,
            venue.address,
            venue.region,
            venue.latitude,
            venue.longitude,
            venue.phone,
            venue.imageUrl,
            venue.viewBoxWidth,
            venue.viewBoxHeight,
            venue.seatDiameter
        };
    }

    private VenueSummary toSummary(final Tuple tuple) {
        return new VenueSummary(
                required(tuple, venue.id),
                tuple.get(venue.name),
                tuple.get(venue.address),
                tuple.get(venue.region),
                tuple.get(venue.latitude),
                tuple.get(venue.longitude),
                tuple.get(venue.phone),
                tuple.get(venue.imageUrl),
                new VenueSummary.SeatMapLayout(
                        required(tuple, venue.viewBoxWidth),
                        required(tuple, venue.viewBoxHeight),
                        required(tuple, venue.seatDiameter)));
    }

    /**
     * Querydsl {@code Tuple.get}은 projection slot이 없을 수 있어 nullable이지만 여기서는 아니다 -- 위 {@link
     * #row()}가 선택한 컬럼이고, {@code venue.id}는 PK이며 나머지는 전부 {@code Venue.create}가 반드시 받는 값이라 저장된 행에
     * null일 수 없다. 값 하나하나에 {@code Objects.requireNonNull}을 쓰면 어느 컬럼인지가 잡음에 묻혀 helper로 뺀다.
     */
    private <T> T required(final Tuple tuple, final Expression<T> column) {
        return Objects.requireNonNull(tuple.get(column), () -> column + "은 NOT NULL이다");
    }
}
