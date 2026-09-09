package com.ticket.show.infrastructure;

import com.ticket.show.application.port.ShowListQueryPort;

import com.ticket.show.application.port.ShowListQueryPort;
import com.ticket.show.application.ShowSort;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.domain.ShowCardImagePathConverter;
import com.ticket.show.domain.Show;
import com.ticket.show.infrastructure.QuerydslShowSortResolver.SortOrder;
import com.ticket.show.application.LatestShowRow;
import com.ticket.show.application.SaleOpeningSoonDetailRow;
import com.ticket.show.application.SaleOpeningSoonSearchParam;
import com.ticket.show.application.SaleOpeningSoonSummaryRow;
import com.ticket.show.application.ShowListItemRow;
import com.ticket.show.application.ShowParam;
import com.ticket.show.application.ShowSearchCriteria;
import com.ticket.show.application.ShowSearchItemRow;
import com.ticket.show.application.ShowCursor;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.ticket.show.domain.QShowGenre.showGenre;
import static com.ticket.show.domain.QCategory.category;
import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.QShow.show;

/**
 * show 자기 DB에서 공연 목록·검색 데이터를 읽는 persistence adapter다. venue 표시값 조합은
 * 여기서 하지 않는다 — {@code venueId} scalar만 담은 raw row를 돌려주고, 실제 venue 조회·조합은
 * 이 포트를 부르는 use case(application)가 한다.
 */
@Repository
@RequiredArgsConstructor
public class QuerydslShowListQueryPort implements ShowListQueryPort {

    private final JPAQueryFactory queryFactory;
    private final QuerydslShowPredicates queryHelper;
    private final QuerydslShowConditionBuilder showConditionFactory;
    private final QuerydslShowSortResolver sortSupport;
    private final QuerydslShowCursorConditionBuilder showCursorPolicy;
    private final ShowCardImagePathConverter showCardImagePathConverter;

    @Override
    public CursorPage<ShowListItemRow, ShowCursor> findAllBySearch(final ShowParam param, final int size, final ShowSort sort) {
        final SortOrder sortOrder = sortSupport.resolveSortOrder(sort);
        final BooleanBuilder where = showConditionFactory.buildMainListCondition(param, sortOrder);

        return findCursorPage(
                size,
                param.getCursor(),
                where,
                sortOrder,
                this::fetchShowPageRows,
                (context, ids) -> fetchMainShowResponses(ids, context.primaryOrder(), context.tieBreakerOrder())
        );
    }

    @Override
    public List<LatestShowRow> findLatestShows(final String categoryCode, final int limit) {
        final List<Tuple> rows = queryFactory
                .select(show.id, show.title, show.image, show.startDate, show.endDate, show.venueId, show.createdAt)
                .distinct()
                .from(show)
                .leftJoin(showGenre).on(showGenre.showId.eq(show.id))
                .leftJoin(genre).on(showGenre.genreId.eq(genre.id))
                .leftJoin(category).on(genre.categoryId.eq(category.id))
                .where(queryHelper.categoryCodeEq(categoryCode))
                .orderBy(show.createdAt.desc())
                .limit(limit)
                .fetch();

        return rows.stream().map(this::toLatestShowRow).toList();
    }

    @Override
    public List<SaleOpeningSoonSummaryRow> findShowsSaleOpeningSoon(final String categoryCode, final int limit) {
        final List<Tuple> rows = queryFactory
                .select(show.id, show.title, show.image, show.venueId, show.displaySaleWindow.startsAt)
                .distinct()
                .from(show)
                .leftJoin(showGenre).on(showGenre.showId.eq(show.id))
                .leftJoin(genre).on(showGenre.genreId.eq(genre.id))
                .leftJoin(category).on(genre.categoryId.eq(category.id))
                .where(showConditionFactory.buildSaleOpeningSoonSummaryCondition(categoryCode))
                .orderBy(show.displaySaleWindow.startsAt.asc())
                .limit(limit)
                .fetch();

        return rows.stream().map(this::toSaleOpeningSoonSummaryRow).toList();
    }

    @Override
    public CursorPage<SaleOpeningSoonDetailRow, ShowCursor> findSaleOpeningSoonPage(
            final SaleOpeningSoonSearchParam param,
            final int size,
            final ShowSort sort
    ) {
        final SortOrder sortOrder = sortSupport.resolveSortOrder(sort);
        final BooleanBuilder where = showConditionFactory.buildSaleOpeningCondition(param);

        return findCursorPage(
                size,
                param.getCursor(),
                where,
                sortOrder,
                this::fetchShowPageRows,
                (context, ids) -> fetchSaleOpeningResponses(ids, context.primaryOrder(), context.tieBreakerOrder())
        );
    }

    @Override
    public CursorPage<ShowSearchItemRow, ShowCursor> searchShows(
            final ShowSearchCriteria request,
            final int size,
            final ShowSort sort
    ) {
        final SortOrder sortOrder = sortSupport.resolveSortOrder(sort);
        final BooleanBuilder where = showConditionFactory.buildSearchCondition(request, sortOrder);

        return findCursorPage(
                size,
                request.getCursor(),
                where,
                sortOrder,
                this::fetchShowPageRows,
                (context, ids) -> fetchSearchResponses(ids, context.primaryOrder(), context.tieBreakerOrder())
        );
    }

    @Override
    public long countSearchShows(final ShowSearchCriteria request) {
        final BooleanBuilder where = showConditionFactory.buildSearchCondition(request, null);
        final Long count = queryFactory
                .select(show.id.countDistinct())
                .from(show)
                .leftJoin(showGenre).on(showGenre.showId.eq(show.id))
                .leftJoin(genre).on(showGenre.genreId.eq(genre.id))
                .leftJoin(category).on(genre.categoryId.eq(category.id))
                .where(where)
                .fetchOne();
        return count != null ? count : 0L;
    }

    private <T> CursorPage<T, ShowCursor> findCursorPage(
            final int size,
            final ShowCursor cursor,
            final BooleanBuilder where,
            final SortOrder sortOrder,
            final Function<QueryPageContext, List<Tuple>> rowFetcher,
            final BiFunction<QueryPageContext, List<Long>, List<T>> resultFetcher
    ) {
        showCursorPolicy.applyCursor(where, cursor, sortOrder);

        final QueryPageContext context = new QueryPageContext(
                size,
                where,
                sortOrder,
                sortSupport.primaryOrderSpecifier(sortOrder),
                sortSupport.tieBreakerOrder(sortOrder)
        );

        final List<Tuple> rows = rowFetcher.apply(context);
        final List<Long> ids = extractIds(rows);
        if (ids.isEmpty()) {
            return CursorPage.empty();
        }

        final List<T> results = new ArrayList<>(resultFetcher.apply(context, ids));
        final boolean hasNext = results.size() > size;
        final List<T> pageResults = hasNext ? results.subList(0, size) : results;

        final ShowCursor nextPosition = hasNext
                ? showCursorPolicy.buildNextPosition(rows, size, sortOrder)
                : null;

        return new CursorPage<>(List.copyOf(pageResults), hasNext, nextPosition);
    }

    private List<Tuple> fetchShowPageRows(final QueryPageContext context) {
        return queryFactory
                .select(show.id, show.startDate, show.createdAt, show.displaySaleWindow.startsAt, show.viewCount)
                .distinct()
                .from(show)
                .leftJoin(showGenre).on(showGenre.showId.eq(show.id))
                .leftJoin(genre).on(showGenre.genreId.eq(genre.id))
                .leftJoin(category).on(genre.categoryId.eq(category.id))
                .where(context.where())
                .orderBy(context.primaryOrder(), context.tieBreakerOrder())
                .limit(context.size() + 1L)
                .fetch();
    }

    private List<ShowListItemRow> fetchMainShowResponses(
            final List<Long> ids,
            final OrderSpecifier<?> primaryOrder,
            final OrderSpecifier<Long> tieBreakerOrder
    ) {
        final Map<Long, List<String>> genreMap = fetchGenreMap(ids);
        final List<Show> shows = queryFactory
                .selectFrom(show)
                .where(show.id.in(ids))
                .orderBy(primaryOrder, tieBreakerOrder)
                .fetch();

        return new ArrayList<>(shows.stream()
                .map(s -> new ShowListItemRow(
                        s.getId(),
                        s.getTitle(),
                        s.getSubTitle(),
                        showCardImagePathConverter.toCardImage(s.getImage()),
                        genreMap.getOrDefault(s.getId(), List.of()),
                        s.getStartDate(),
                        s.getEndDate(),
                        s.getViewCount(),
                        s.getDisplaySaleType(),
                        s.getDisplaySaleStartsAt(),
                        s.getDisplaySaleEndsAt(),
                        s.getCreatedAt(),
                        s.getVenueId()
                ))
                .toList());
    }

    private List<SaleOpeningSoonDetailRow> fetchSaleOpeningResponses(
            final List<Long> ids,
            final OrderSpecifier<?> primaryOrder,
            final OrderSpecifier<Long> tieBreakerOrder
    ) {
        final List<Tuple> rows = queryFactory
                .select(show.id, show.title, show.subTitle, show.image, show.venueId,
                        show.startDate, show.endDate, show.displaySaleWindow.startsAt, show.displaySaleWindow.endsAt, show.viewCount)
                .from(show)
                .where(show.id.in(ids))
                .orderBy(primaryOrder, tieBreakerOrder)
                .fetch();

        return rows.stream().map(this::toSaleOpeningSoonDetailRow).toList();
    }

    private List<ShowSearchItemRow> fetchSearchResponses(
            final List<Long> ids,
            final OrderSpecifier<?> primaryOrder,
            final OrderSpecifier<Long> tieBreakerOrder
    ) {
        final List<Tuple> rows = queryFactory
                .select(show.id, show.title, show.image, show.venueId,
                        show.startDate, show.endDate, show.viewCount)
                .from(show)
                .where(show.id.in(ids))
                .orderBy(primaryOrder, tieBreakerOrder)
                .fetch();

        return rows.stream().map(this::toShowSearchItemRow).toList();
    }

    private List<Long> extractIds(final List<Tuple> rows) {
        return rows.stream().map(t -> t.get(show.id)).toList();
    }

    private LatestShowRow toLatestShowRow(final Tuple tuple) {
        return new LatestShowRow(
                tuple.get(show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(show.venueId),
                tuple.get(show.createdAt)
        );
    }

    private SaleOpeningSoonSummaryRow toSaleOpeningSoonSummaryRow(final Tuple tuple) {
        return new SaleOpeningSoonSummaryRow(
                tuple.get(show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.venueId),
                tuple.get(show.displaySaleWindow.startsAt)
        );
    }

    private SaleOpeningSoonDetailRow toSaleOpeningSoonDetailRow(final Tuple tuple) {
        return new SaleOpeningSoonDetailRow(
                tuple.get(show.id),
                tuple.get(show.title),
                tuple.get(show.subTitle),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(show.displaySaleWindow.startsAt),
                tuple.get(show.displaySaleWindow.endsAt),
                tuple.get(show.viewCount),
                tuple.get(show.venueId)
        );
    }

    private ShowSearchItemRow toShowSearchItemRow(final Tuple tuple) {
        return new ShowSearchItemRow(
                tuple.get(show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(show.viewCount),
                tuple.get(show.venueId)
        );
    }

    private Map<Long, List<String>> fetchGenreMap(final List<Long> ids) {
        final List<Tuple> genreTuples = queryFactory
                .select(show.id, genre.name)
                .from(show)
                .leftJoin(showGenre).on(showGenre.showId.eq(show.id))
                .leftJoin(genre).on(showGenre.genreId.eq(genre.id))
                .where(show.id.in(ids))
                .fetch();

        final Map<Long, List<String>> genreMap = new LinkedHashMap<>();
        for (Tuple tuple : genreTuples) {
            final Long showId = tuple.get(show.id);
            final String genreName = tuple.get(genre.name);
            if (genreName != null) {
                genreMap.computeIfAbsent(showId, key -> new ArrayList<>()).add(genreName);
            }
        }
        return genreMap;
    }

    private record QueryPageContext(
            int size,
            BooleanBuilder where,
            SortOrder sortOrder,
            OrderSpecifier<?> primaryOrder,
            OrderSpecifier<Long> tieBreakerOrder
    ) {
    }
}
