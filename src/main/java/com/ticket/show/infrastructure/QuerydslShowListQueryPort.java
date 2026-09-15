package com.ticket.show.infrastructure;

import static com.ticket.show.domain.QCategory.category;
import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.domain.show.QShowGenre.showGenre;
import static com.ticket.show.infrastructure.QuerydslTupleColumns.required;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.shared.api.CursorPage;
import com.ticket.show.application.LatestShowRow;
import com.ticket.show.application.SaleOpeningSoonDetailRow;
import com.ticket.show.application.SaleOpeningSoonSearchParam;
import com.ticket.show.application.SaleOpeningSoonSummaryRow;
import com.ticket.show.application.ShowCursor;
import com.ticket.show.application.ShowListItemRow;
import com.ticket.show.application.ShowListParam;
import com.ticket.show.application.ShowSearchCriteria;
import com.ticket.show.application.ShowSearchItemRow;
import com.ticket.show.application.ShowSort;
import com.ticket.show.application.port.ShowListQueryPort;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;
import com.ticket.show.infrastructure.QuerydslShowSortResolver.SortOrder;

import lombok.RequiredArgsConstructor;

/**
 * show 자기 DB에서 공연 목록·검색 데이터를 읽는 persistence adapter다. venue 표시값 조합은 여기서 하지 않는다 — {@code venueId}
 * scalar만 담은 raw row를 돌려주고, 실제 venue 조회·조합은 이 포트를 부르는 use case(application)가 한다.
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
    public CursorPage<ShowListItemRow, ShowCursor> findAllBySearch(
            final ShowListParam param, final int size, final ShowSort sort) {
        final SortOrder sortOrder = sortSupport.resolveSortOrder(sort, param.getCursor());
        final BooleanBuilder where = showConditionFactory.buildMainListCondition(param, sortOrder);

        return findCursorPage(
                size,
                param.getCursor(),
                where,
                sortOrder,
                (orders, ids) -> fetchMainShowResponses(ids, orders));
    }

    /**
     * 상단 최신 공연 배너다. 전체 목록의 최신순과 같은 순서를 쓴다 — <b>마감되지 않은 공연 먼저, 등록일 내림차순, 등록일이 같으면 id 내림차순</b>. 배너와
     * 목록이 다른 순서를 쓰면 같은 화면에서 "최신"의 의미가 둘이 된다.
     */
    @Override
    public List<LatestShowRow> findLatestShows(final String categoryCode, final int limit) {
        final SortOrder sortOrder = sortSupport.resolveSortOrder(ShowSort.LATEST);
        final List<Tuple> rows =
                queryFactory
                        .select(
                                show.id,
                                show.title,
                                show.image,
                                show.startDate,
                                show.endDate,
                                show.venueId,
                                show.createdAt)
                        .from(show)
                        .leftJoin(showGenre)
                        .on(showGenre.showId.eq(show.id))
                        .leftJoin(genre)
                        .on(showGenre.genreId.eq(genre.id))
                        .leftJoin(category)
                        .on(genre.categoryId.eq(category.id))
                        .where(queryHelper.categoryCodeEq(categoryCode))
                        // DISTINCT 대신 GROUP BY인 이유는 fetchShowPageRows와 같다.
                        .groupBy(
                                show.id,
                                show.title,
                                show.image,
                                show.startDate,
                                show.endDate,
                                show.venueId,
                                show.createdAt)
                        .orderBy(sortSupport.orderSpecifiers(sortOrder))
                        .limit(limit)
                        .fetch();

        return rows.stream().map(this::toLatestShowRow).toList();
    }

    @Override
    public List<SaleOpeningSoonSummaryRow> findSaleOpeningSoonSummaries(
            final String categoryCode, final int limit) {
        final List<Tuple> rows =
                queryFactory
                        .select(
                                show.id,
                                show.title,
                                show.image,
                                show.venueId,
                                show.displaySaleWindow.startsAt)
                        .distinct()
                        .from(show)
                        .leftJoin(showGenre)
                        .on(showGenre.showId.eq(show.id))
                        .leftJoin(genre)
                        .on(showGenre.genreId.eq(genre.id))
                        .leftJoin(category)
                        .on(genre.categoryId.eq(category.id))
                        .where(
                                showConditionFactory.buildSaleOpeningSoonSummaryCondition(
                                        categoryCode))
                        .orderBy(show.displaySaleWindow.startsAt.asc())
                        .limit(limit)
                        .fetch();

        return rows.stream().map(this::toSaleOpeningSoonSummaryRow).toList();
    }

    @Override
    public CursorPage<SaleOpeningSoonDetailRow, ShowCursor> findSaleOpeningSoonPage(
            final SaleOpeningSoonSearchParam param, final int size, final ShowSort sort) {
        final SortOrder sortOrder = sortSupport.resolveSortOrder(sort, param.getCursor());
        final BooleanBuilder where = showConditionFactory.buildSaleOpeningSoonCondition(param);

        return findCursorPage(
                size,
                param.getCursor(),
                where,
                sortOrder,
                (orders, ids) -> fetchSaleOpeningSoonResponses(ids, orders));
    }

    @Override
    public CursorPage<ShowSearchItemRow, ShowCursor> searchShows(
            final ShowSearchCriteria criteria, final int size, final ShowSort sort) {
        final SortOrder sortOrder = sortSupport.resolveSortOrder(sort, criteria.getCursor());
        final BooleanBuilder where = showConditionFactory.buildSearchCondition(criteria, sortOrder);

        return findCursorPage(
                size,
                criteria.getCursor(),
                where,
                sortOrder,
                (orders, ids) -> fetchSearchResponses(ids, orders));
    }

    @Override
    public long countSearchShows(final ShowSearchCriteria criteria) {
        final BooleanBuilder where = showConditionFactory.buildSearchCondition(criteria, null);
        final Long count =
                queryFactory
                        .select(show.id.countDistinct())
                        .from(show)
                        .leftJoin(showGenre)
                        .on(showGenre.showId.eq(show.id))
                        .leftJoin(genre)
                        .on(showGenre.genreId.eq(genre.id))
                        .leftJoin(category)
                        .on(genre.categoryId.eq(category.id))
                        .where(where)
                        .fetchOne();
        return count != null ? count : 0L;
    }

    /**
     * 커서 페이지 한 장을 읽는다. 1단계에서 정렬·커서로 id를 뽑고, 2단계에서 그 id로 본문을 다시 읽는다.
     *
     * <p><b>2단계에도 1단계와 같은 {@code orders}를 건다.</b> {@code IN (...)} 조회가 돌려주는 순서를 믿지 않기 위해서다. 그래서 어떤
     * 본문을 읽을지({@code resultFetcher})만 호출자가 정하고, 나머지 -- 1단계 query, 페이지 크기 계산, 다음 커서 -- 는 여기서 한 번만
     * 정의한다.
     */
    private <T> CursorPage<T, ShowCursor> findCursorPage(
            final int size,
            final @Nullable ShowCursor cursor,
            final BooleanBuilder where,
            final SortOrder sortOrder,
            final BiFunction<OrderSpecifier<?>[], List<Long>, List<T>> resultFetcher) {
        showCursorPolicy.applyCursor(where, cursor, sortOrder);

        final OrderSpecifier<?>[] orders = sortSupport.orderSpecifiers(sortOrder);

        final List<Tuple> rows = fetchShowPageRows(where, orders, size);
        final List<Long> ids = extractIds(rows);
        if (ids.isEmpty()) {
            return CursorPage.empty();
        }

        final List<T> results = new ArrayList<>(resultFetcher.apply(orders, ids));
        final boolean hasNext = results.size() > size;
        final List<T> pageResults = hasNext ? results.subList(0, size) : results;

        final ShowCursor nextPosition =
                hasNext ? showCursorPolicy.buildNextPosition(rows, size, sortOrder) : null;

        return new CursorPage<>(List.copyOf(pageResults), hasNext, nextPosition);
    }

    private List<Tuple> fetchShowPageRows(
            final BooleanBuilder where, final OrderSpecifier<?>[] orders, final int size) {
        return queryFactory
                .select(pageRowColumns())
                .from(show)
                .leftJoin(showGenre)
                .on(showGenre.showId.eq(show.id))
                .leftJoin(genre)
                .on(showGenre.genreId.eq(genre.id))
                .leftJoin(category)
                .on(genre.categoryId.eq(category.id))
                .where(where)
                // DISTINCT가 아니라 GROUP BY로 장르 조인의 행 중복을 없앤다. SELECT DISTINCT는
                // ORDER BY에 쓴 식이 select 목록에 "그대로" 있어야 하는데(H2 / Oracle ORA-01791),
                // 최신순의 마감 여부 CASE는 바인딩 파라미터를 써서 두 자리가 같은 식으로 인정되지
                // 않는다. GROUP BY는 그 제약을 받지 않는다.
                .groupBy(pageRowColumns())
                .orderBy(orders)
                .limit(size + 1L)
                .fetch();
    }

    /** select와 GROUP BY가 같아야 조인으로 늘어난 행이 정확히 하나로 접힌다. */
    private static Expression<?>[] pageRowColumns() {
        return new Expression<?>[] {
            show.id,
            show.startDate,
            show.createdAt,
            show.displaySaleWindow.startsAt,
            // 다음 커서의 마감 여부 판정에 쓴다(QuerydslShowCursorConditionBuilder).
            show.displaySaleWindow.endsAt,
            show.viewCount
        };
    }

    private List<ShowListItemRow> fetchMainShowResponses(
            final List<Long> ids, final OrderSpecifier<?>[] orders) {
        final Map<Long, List<String>> genreMap = fetchGenreMap(ids);
        final List<Show> shows =
                queryFactory.selectFrom(show).where(show.id.in(ids)).orderBy(orders).fetch();

        return new ArrayList<>(
                shows.stream()
                        .map(
                                s ->
                                        new ShowListItemRow(
                                                s.getId(),
                                                s.getTitle(),
                                                s.getSubTitle(),
                                                showCardImagePathConverter.toCardImage(
                                                        s.getImage()),
                                                genreMap.getOrDefault(s.getId(), List.of()),
                                                s.getStartDate(),
                                                s.getEndDate(),
                                                s.getViewCount(),
                                                s.getDisplaySaleType(),
                                                s.getDisplaySaleStartsAt(),
                                                s.getDisplaySaleEndsAt(),
                                                s.getCreatedAt(),
                                                s.getVenueId()))
                        .toList());
    }

    private List<SaleOpeningSoonDetailRow> fetchSaleOpeningSoonResponses(
            final List<Long> ids, final OrderSpecifier<?>[] orders) {
        final List<Tuple> rows =
                queryFactory
                        .select(
                                show.id,
                                show.title,
                                show.subTitle,
                                show.image,
                                show.venueId,
                                show.startDate,
                                show.endDate,
                                show.displaySaleWindow.startsAt,
                                show.displaySaleWindow.endsAt,
                                show.viewCount)
                        .from(show)
                        .where(show.id.in(ids))
                        .orderBy(orders)
                        .fetch();

        return rows.stream().map(this::toSaleOpeningSoonDetailRow).toList();
    }

    private List<ShowSearchItemRow> fetchSearchResponses(
            final List<Long> ids, final OrderSpecifier<?>[] orders) {
        final List<Tuple> rows =
                queryFactory
                        .select(
                                show.id,
                                show.title,
                                show.image,
                                show.venueId,
                                show.startDate,
                                show.endDate,
                                show.viewCount)
                        .from(show)
                        .where(show.id.in(ids))
                        .orderBy(orders)
                        .fetch();

        return rows.stream().map(this::toShowSearchItemRow).toList();
    }

    private List<Long> extractIds(final List<Tuple> rows) {
        return rows.stream().map(t -> t.get(show.id)).toList();
    }

    private LatestShowRow toLatestShowRow(final Tuple tuple) {
        return new LatestShowRow(
                required(tuple, show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(show.venueId),
                required(tuple, show.createdAt));
    }

    private SaleOpeningSoonSummaryRow toSaleOpeningSoonSummaryRow(final Tuple tuple) {
        return new SaleOpeningSoonSummaryRow(
                required(tuple, show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.venueId),
                tuple.get(show.displaySaleWindow.startsAt));
    }

    private SaleOpeningSoonDetailRow toSaleOpeningSoonDetailRow(final Tuple tuple) {
        return new SaleOpeningSoonDetailRow(
                required(tuple, show.id),
                tuple.get(show.title),
                tuple.get(show.subTitle),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(show.displaySaleWindow.startsAt),
                tuple.get(show.displaySaleWindow.endsAt),
                required(tuple, show.viewCount),
                tuple.get(show.venueId));
    }

    private ShowSearchItemRow toShowSearchItemRow(final Tuple tuple) {
        return new ShowSearchItemRow(
                required(tuple, show.id),
                tuple.get(show.title),
                showCardImagePathConverter.toCardImage(tuple.get(show.image)),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                required(tuple, show.viewCount),
                tuple.get(show.venueId));
    }

    private Map<Long, List<String>> fetchGenreMap(final List<Long> ids) {
        final List<Tuple> genreTuples =
                queryFactory
                        .select(show.id, genre.name)
                        .from(show)
                        .leftJoin(showGenre)
                        .on(showGenre.showId.eq(show.id))
                        .leftJoin(genre)
                        .on(showGenre.genreId.eq(genre.id))
                        .where(show.id.in(ids))
                        .fetch();

        final Map<Long, List<String>> genreMap = new LinkedHashMap<>();
        for (Tuple tuple : genreTuples) {
            final Long showId = required(tuple, show.id);
            final String genreName = tuple.get(genre.name);
            if (genreName != null) {
                genreMap.computeIfAbsent(showId, key -> new ArrayList<>()).add(genreName);
            }
        }
        return genreMap;
    }
}
