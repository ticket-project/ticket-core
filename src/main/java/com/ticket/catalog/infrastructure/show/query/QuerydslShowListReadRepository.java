package com.ticket.catalog.infrastructure.show.query;

import com.ticket.catalog.application.show.query.ShowListReadRepository;
import com.ticket.catalog.application.show.query.ShowSort;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.catalog.domain.show.image.ShowCardImagePathConverter;
import com.ticket.catalog.domain.show.Show;
import com.ticket.catalog.infrastructure.show.query.QuerydslShowSortResolver.SortOrder;
import com.ticket.catalog.application.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.catalog.application.show.query.model.ShowListItemView;
import com.ticket.catalog.application.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.catalog.application.show.query.model.ShowOpeningSoonSummaryView;
import com.ticket.catalog.application.show.query.model.ShowParam;
import com.ticket.catalog.application.show.query.model.ShowSearchCriteria;
import com.ticket.catalog.application.show.query.model.ShowSearchItemView;
import com.ticket.catalog.application.show.query.model.ShowSummaryView;
import com.ticket.catalog.application.show.query.model.ShowCursor;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.ticket.catalog.domain.show.QShowGenre.showGenre;
import static com.ticket.catalog.domain.show.QCategory.category;
import static com.ticket.catalog.domain.show.QGenre.genre;
import static com.ticket.catalog.domain.show.QShow.show;
import static com.ticket.catalog.domain.show.QVenue.venue;

@Repository
@RequiredArgsConstructor
public class QuerydslShowListReadRepository implements ShowListReadRepository {

    private final JPAQueryFactory queryFactory;
    private final QuerydslShowPredicates queryHelper;
    private final QuerydslShowConditionBuilder showConditionFactory;
    private final QuerydslShowSortResolver sortSupport;
    private final QuerydslShowCursorConditionBuilder showCursorPolicy;
    private final ShowCardImagePathConverter showCardImagePathConverter;

    @Override
    public CursorPage<ShowListItemView, ShowCursor> findAllBySearch(final ShowParam param, final int size, final ShowSort sort) {
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
    public List<ShowSummaryView> findLatestShows(final String categoryCode, final int limit) {
        return queryFactory
                .select(show.id, show.title, show.image, show.startDate, show.endDate, venue.name, show.createdAt)
                .distinct()
                .from(show)
                .leftJoin(show.venue, venue)
                .leftJoin(showGenre).on(showGenre.show.eq(show))
                .leftJoin(genre).on(showGenre.genre.eq(genre))
                .leftJoin(category).on(genre.category.eq(category))
                .where(queryHelper.categoryCodeEq(categoryCode))
                .orderBy(show.createdAt.desc())
                .limit(limit)
                .fetch()
                .stream()
                .map(this::toShowSummaryResponse)
                .toList();
    }

    @Override
    public List<ShowOpeningSoonSummaryView> findShowsSaleOpeningSoon(final String categoryCode, final int limit) {
        return queryFactory
                .select(show.id, show.title, show.image, venue.name, show.saleStartDate)
                .distinct()
                .from(show)
                .leftJoin(show.venue, venue)
                .leftJoin(showGenre).on(showGenre.show.eq(show))
                .leftJoin(genre).on(showGenre.genre.eq(genre))
                .leftJoin(category).on(genre.category.eq(category))
                .where(showConditionFactory.buildSaleOpeningSoonSummaryCondition(categoryCode))
                .orderBy(show.saleStartDate.asc())
                .limit(limit)
                .fetch()
                .stream()
                .map(this::toShowOpeningSoonSummaryResponse)
                .toList();
    }

    @Override
    public CursorPage<ShowOpeningSoonDetailView, ShowCursor> findSaleOpeningSoonPage(
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
    public CursorPage<ShowSearchItemView, ShowCursor> searchShows(
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
                .leftJoin(showGenre).on(showGenre.show.eq(show))
                .leftJoin(genre).on(showGenre.genre.eq(genre))
                .leftJoin(category).on(genre.category.eq(category))
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
                .select(show.id, show.startDate, show.createdAt, show.saleStartDate, show.viewCount)
                .distinct()
                .from(show)
                .leftJoin(showGenre).on(showGenre.show.eq(show))
                .leftJoin(genre).on(showGenre.genre.eq(genre))
                .leftJoin(category).on(genre.category.eq(category))
                .where(context.where())
                .orderBy(context.primaryOrder(), context.tieBreakerOrder())
                .limit(context.size() + 1L)
                .fetch();
    }

    private List<ShowListItemView> fetchMainShowResponses(
            final List<Long> ids,
            final OrderSpecifier<?> primaryOrder,
            final OrderSpecifier<Long> tieBreakerOrder
    ) {
        final Map<Long, List<String>> genreMap = fetchGenreMap(ids);
        final List<Show> shows = queryFactory
                .selectFrom(show)
                .leftJoin(show.venue, venue).fetchJoin()
                .where(show.id.in(ids))
                .orderBy(primaryOrder, tieBreakerOrder)
                .fetch();

        return new ArrayList<>(shows.stream()
                .map(s -> new ShowListItemView(
                        s.getId(),
                        s.getTitle(),
                        s.getSubTitle(),
                        showCardImagePathConverter.toCardImage(s.getImage()),
                        genreMap.getOrDefault(s.getId(), List.of()),
                        s.getStartDate(),
                        s.getEndDate(),
                        s.getViewCount(),
                        s.getSaleType(),
                        s.getSaleStartDate(),
                        s.getSaleEndDate(),
                        s.getCreatedAt(),
                        s.getVenue() != null ? s.getVenue().getRegion() : null,
                        s.getVenue() != null ? s.getVenue().getName() : null
                ))
                .toList());
    }

    private List<ShowOpeningSoonDetailView> fetchSaleOpeningResponses(
            final List<Long> ids,
            final OrderSpecifier<?> primaryOrder,
            final OrderSpecifier<Long> tieBreakerOrder
    ) {
        return queryFactory
                .select(show.id, show.title, show.subTitle, show.image, venue.name, venue.region,
                        show.startDate, show.endDate, show.saleStartDate, show.saleEndDate, show.viewCount)
                .from(show)
                .leftJoin(show.venue, venue)
                .where(show.id.in(ids))
                .orderBy(primaryOrder, tieBreakerOrder)
                .fetch()
                .stream()
                .map(this::toShowOpeningSoonDetailResponse)
                .toList();
    }

    private List<ShowSearchItemView> fetchSearchResponses(
            final List<Long> ids,
            final OrderSpecifier<?> primaryOrder,
            final OrderSpecifier<Long> tieBreakerOrder
    ) {
        return queryFactory
                .select(show.id, show.title, show.image, venue.name,
                        show.startDate, show.endDate, venue.region, show.viewCount)
                .from(show)
                .leftJoin(show.venue, venue)
                .where(show.id.in(ids))
                .orderBy(primaryOrder, tieBreakerOrder)
                .fetch()
                .stream()
                .map(this::toShowSearchResponse)
                .toList();
    }

    private List<Long> extractIds(final List<Tuple> rows) {
        return rows.stream().map(t -> t.get(show.id)).toList();
    }

    private ShowSummaryView toShowSummaryResponse(final Tuple tuple) {
        return new ShowSummaryView(
                tuple.get(show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(venue.name),
                tuple.get(show.createdAt)
        );
    }

    private ShowOpeningSoonSummaryView toShowOpeningSoonSummaryResponse(final Tuple tuple) {
        return new ShowOpeningSoonSummaryView(
                tuple.get(show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(venue.name),
                tuple.get(show.saleStartDate)
        );
    }

    private ShowOpeningSoonDetailView toShowOpeningSoonDetailResponse(final Tuple tuple) {
        return new ShowOpeningSoonDetailView(
                tuple.get(show.id),
                tuple.get(show.title),
                tuple.get(show.subTitle),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(venue.name),
                tuple.get(venue.region),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(show.saleStartDate),
                tuple.get(show.saleEndDate),
                tuple.get(show.viewCount)
        );
    }

    private ShowSearchItemView toShowSearchResponse(final Tuple tuple) {
        return new ShowSearchItemView(
                tuple.get(show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(venue.name),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(venue.region),
                tuple.get(show.viewCount)
        );
    }

    private Map<Long, List<String>> fetchGenreMap(final List<Long> ids) {
        final List<Tuple> genreTuples = queryFactory
                .select(show.id, genre.name)
                .from(show)
                .leftJoin(showGenre).on(showGenre.show.eq(show))
                .leftJoin(genre).on(showGenre.genre.eq(genre))
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
