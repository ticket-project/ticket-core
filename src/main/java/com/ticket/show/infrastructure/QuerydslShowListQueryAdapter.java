package com.ticket.show.infrastructure;

import static com.ticket.show.domain.QCategory.category;
import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.domain.show.QShowGenre.showGenre;
import static com.ticket.show.infrastructure.QuerydslTupleColumns.required;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
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
public class QuerydslShowListQueryAdapter implements ShowListQueryPort {
    private final JPAQueryFactory queryFactory;
    private final QuerydslShowSortResolver sortResolver;
    private final QuerydslShowCursorConditionBuilder cursorConditionBuilder;
    private final SaleDisplayStatusPredicates saleDisplayStatusPredicates;
    private final ShowCardImagePathConverter showCardImagePathConverter;
    private final Clock clock;

    @Override
    public CursorPage<ShowListItemRow, ShowCursor> findAllBySearch(
            final ShowListParam param,
            final @Nullable Set<Long> venueIds,
            final int size,
            final ShowSort sort) {
        final SortOrder sortOrder = sortResolver.resolveSortOrder(sort, param.getCursor());
        final BooleanBuilder where = mainListCondition(param, venueIds, sortOrder);

        return findCursorPage(size, param.getCursor(), where, sortOrder, this::fetchShowListRows);
    }

    /**
     * 상단 최신 공연 배너다. 전체 목록의 최신순과 같은 순서를 쓴다 — <b>마감되지 않은 공연 먼저, 등록일 내림차순, 등록일이 같으면 id 내림차순</b>. 배너와
     * 목록이 다른 순서를 쓰면 같은 화면에서 "최신"의 의미가 둘이 된다.
     */
    @Override
    public List<LatestShowRow> findLatestShows(final String categoryCode, final int limit) {
        final SortOrder sortOrder = sortResolver.resolveSortOrder(ShowSort.LATEST);
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
                        .where(categoryCodeEq(categoryCode))
                        // DISTINCT 대신 GROUP BY인 이유는 fetchShowPageRows와 같다.
                        .groupBy(
                                show.id,
                                show.title,
                                show.image,
                                show.startDate,
                                show.endDate,
                                show.venueId,
                                show.createdAt)
                        .orderBy(sortResolver.orderSpecifiers(sortOrder))
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
                        .where(saleOpeningSoonSummaryCondition(categoryCode))
                        .orderBy(show.displaySaleWindow.startsAt.asc())
                        .limit(limit)
                        .fetch();

        return rows.stream().map(this::toSaleOpeningSoonSummaryRow).toList();
    }

    @Override
    public CursorPage<SaleOpeningSoonDetailRow, ShowCursor> findSaleOpeningSoonPage(
            final SaleOpeningSoonSearchParam param,
            final @Nullable Set<Long> venueIds,
            final int size,
            final ShowSort sort) {
        final SortOrder sortOrder = sortResolver.resolveSortOrder(sort, param.getCursor());
        final BooleanBuilder where = saleOpeningSoonCondition(param, venueIds);

        return findCursorPage(
                size, param.getCursor(), where, sortOrder, this::fetchSaleOpeningSoonDetailRows);
    }

    @Override
    public CursorPage<ShowSearchItemRow, ShowCursor> searchShows(
            final ShowSearchCriteria criteria,
            final @Nullable Set<Long> venueIds,
            final int size,
            final ShowSort sort) {
        final SortOrder sortOrder = sortResolver.resolveSortOrder(sort, criteria.getCursor());
        final BooleanBuilder where = searchCondition(criteria, venueIds, sortOrder);

        return findCursorPage(
                size, criteria.getCursor(), where, sortOrder, this::fetchShowSearchRows);
    }

    @Override
    public long countSearchShows(
            final ShowSearchCriteria criteria, final @Nullable Set<Long> venueIds) {
        final BooleanBuilder where = searchCondition(criteria, venueIds, null);
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

    // 검색 조건 조립 ------------------------------------------------------------
    //
    // 각 query가 실제로 무엇으로 거르는지가 query 바로 옆에서 읽혀야 한다. 조건 조립을 별도 bean으로
    // 빼면 호출부에는 "조건을 만든다"만 남고 keyword·category·genre·region·기간·판매 상태라는 사실이
    // 사라진다. 값이 없는 조건은 null을 돌려주고 BooleanBuilder가 그것을 "조건 없음"으로 건너뛴다.

    /** 메인 목록: 카테고리 · 지역 · 장르. 공연 임박순으로 정렬할 때만 이미 시작한 공연을 뺀다. */
    private BooleanBuilder mainListCondition(
            final ShowListParam param,
            final @Nullable Set<Long> venueIds,
            final SortOrder sortOrder) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(categoryCodeEq(param.getCategory()));
        where.and(venueIdIn(venueIds));
        where.and(genreCodeEq(param.getGenre()));
        appendShowStartApproachingCondition(where, sortOrder, LocalDate.now(clock));
        return where;
    }

    /** 오픈 예정 배너: 카테고리 · 아직 판매가 시작되지 않은 공연. */
    private BooleanBuilder saleOpeningSoonSummaryCondition(final String categoryCode) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(categoryCodeEq(categoryCode));
        where.and(show.displaySaleWindow.startsAt.goe(LocalDateTime.now(clock)));
        return where;
    }

    /** 오픈 예정 목록: 아직 판매 전 · 카테고리 · 지역 · 제목 · 판매 시작/종료 기간(각각 열린 구간). */
    private BooleanBuilder saleOpeningSoonCondition(
            final SaleOpeningSoonSearchParam param, final @Nullable Set<Long> venueIds) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(show.displaySaleWindow.startsAt.goe(LocalDateTime.now(clock)));
        where.and(categoryCodeEq(param.getCategory()));
        where.and(venueIdIn(venueIds));
        where.and(titleContains(param.getTitle()));

        final LocalDateTime startsAtFrom = param.getDisplaySaleStartsAtFrom();
        where.and(startsAtFrom != null ? show.displaySaleWindow.startsAt.goe(startsAtFrom) : null);
        final LocalDateTime startsAtTo = param.getDisplaySaleStartsAtTo();
        where.and(startsAtTo != null ? show.displaySaleWindow.startsAt.loe(startsAtTo) : null);
        final LocalDateTime endsAtFrom = param.getDisplaySaleEndsAtFrom();
        where.and(endsAtFrom != null ? show.displaySaleWindow.endsAt.goe(endsAtFrom) : null);
        final LocalDateTime endsAtTo = param.getDisplaySaleEndsAtTo();
        where.and(endsAtTo != null ? show.displaySaleWindow.endsAt.loe(endsAtTo) : null);
        return where;
    }

    /**
     * 검색: 키워드 · 카테고리 · 지역 · 공연 시작일 범위 · 판매 표시 상태.
     *
     * <p>판매 표시 상태 판정 시각과 공연 임박순의 기준 날짜는 <b>같은 now</b>를 쓴다. 집계는 정렬을 모르므로 {@code sortOrder}가 null로
     * 들어오고, 그러면 공연 임박순 조건이 붙지 않는다.
     */
    private BooleanBuilder searchCondition(
            final ShowSearchCriteria criteria,
            final @Nullable Set<Long> venueIds,
            final @Nullable SortOrder sortOrder) {
        final BooleanBuilder where = new BooleanBuilder();
        final LocalDateTime now = LocalDateTime.now(clock);
        where.and(titleContains(criteria.getKeyword()));
        where.and(categoryCodeEq(criteria.getCategory()));
        where.and(venueIdIn(venueIds));

        final LocalDate startDateFrom = criteria.getStartDateFrom();
        where.and(startDateFrom != null ? show.startDate.goe(startDateFrom) : null);
        final LocalDate startDateTo = criteria.getStartDateTo();
        where.and(startDateTo != null ? show.startDate.loe(startDateTo) : null);

        where.and(saleDisplayStatusPredicates.condition(criteria.getSaleDisplayStatus(), now));
        appendShowStartApproachingCondition(where, sortOrder, now.toLocalDate());
        return where;
    }

    /**
     * 지역 조건은 application이 이미 venueId로 해석해 넘긴다({@link com.ticket.show.application.RegionVenueIds}).
     *
     * <p><b>{@code null}과 빈 집합을 같게 다루면 안 된다.</b> null은 지역 조건이 없다는 뜻이라 조건을 걸지 않고, 공연장이 없는 공연까지 전부
     * 나온다. 빈 집합은 조건은 있는데 그 지역에 공연장이 없다는 뜻이고, Querydsl이 {@code in(빈 컬렉션)}을 거짓 조건으로 직렬화해 결과가 0건이 된다 —
     * 이것을 "조건 없음"으로 되돌리면 "그 지역에 공연장이 없다"가 "전체 목록"으로 조용히 바뀐다.
     */
    private static @Nullable BooleanExpression venueIdIn(final @Nullable Set<Long> venueIds) {
        return venueIds == null ? null : show.venueId.in(venueIds);
    }

    private static void appendShowStartApproachingCondition(
            final BooleanBuilder where,
            final @Nullable SortOrder sortOrder,
            final LocalDate today) {
        if (sortOrder != null && ShowSort.SHOW_START_APPROACHING.equals(sortOrder.key())) {
            where.and(show.startDate.goe(today));
        }
    }

    /** 값이 없거나 공백이면 조건을 걸지 않는다 — 빈 문자열을 일치 조건으로 바꾸면 결과가 통째로 0건이 된다. */
    private static @Nullable BooleanExpression categoryCodeEq(final @Nullable String categoryCode) {
        return StringUtils.hasText(categoryCode) ? category.code.eq(categoryCode) : null;
    }

    private static @Nullable BooleanExpression genreCodeEq(final @Nullable String genreCode) {
        return StringUtils.hasText(genreCode) ? genre.code.eq(genreCode) : null;
    }

    /** <b>검색 키워드는 제목만 본다</b> — 부제·출연자·공연장 이름은 대상이 아니다. 대소문자는 무시한다. */
    private static @Nullable BooleanExpression titleContains(final @Nullable String title) {
        return StringUtils.hasText(title) ? show.title.containsIgnoreCase(title) : null;
    }

    /**
     * 커서 페이지 한 장을 읽는다. 1단계에서 정렬·커서로 id를 뽑고, 2단계에서 그 id로 본문을 다시 읽는다.
     *
     * <p><b>2단계에도 1단계와 같은 {@code orderSpecifiers}를 건다.</b> {@code IN (...)} 조회가 돌려주는 순서를 믿지 않기
     * 위해서다. 그래서 어떤 본문을 읽을지({@code rowFetcher})만 호출자가 정하고, 나머지 -- 1단계 query, 페이지 크기 계산, 다음 커서 -- 는
     * 여기서 한 번만 정의한다.
     */
    private <T> CursorPage<T, ShowCursor> findCursorPage(
            final int size,
            final @Nullable ShowCursor cursor,
            final BooleanBuilder where,
            final SortOrder sortOrder,
            final BiFunction<List<Long>, OrderSpecifier<?>[], List<T>> rowFetcher) {
        cursorConditionBuilder.applyCursor(where, cursor, sortOrder);

        final OrderSpecifier<?>[] orderSpecifiers = sortResolver.orderSpecifiers(sortOrder);

        final List<Tuple> rows = fetchShowPageRows(where, orderSpecifiers, size);
        final List<Long> showIds = extractShowIds(rows);
        if (showIds.isEmpty()) {
            return CursorPage.empty();
        }

        final List<T> results = new ArrayList<>(rowFetcher.apply(showIds, orderSpecifiers));
        final boolean hasNext = results.size() > size;
        final List<T> pageResults = hasNext ? results.subList(0, size) : results;

        final ShowCursor nextPosition =
                hasNext ? cursorConditionBuilder.buildNextPosition(rows, size, sortOrder) : null;

        return new CursorPage<>(List.copyOf(pageResults), hasNext, nextPosition);
    }

    private List<Tuple> fetchShowPageRows(
            final BooleanBuilder where, final OrderSpecifier<?>[] orderSpecifiers, final int size) {
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
                .orderBy(orderSpecifiers)
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

    private List<ShowListItemRow> fetchShowListRows(
            final List<Long> showIds, final OrderSpecifier<?>[] orderSpecifiers) {
        final Map<Long, List<String>> genreNamesByShowId = fetchGenreNamesByShowId(showIds);
        final List<Show> shows =
                queryFactory
                        .selectFrom(show)
                        .where(show.id.in(showIds))
                        .orderBy(orderSpecifiers)
                        .fetch();

        return new ArrayList<>(
                shows.stream()
                        .map(
                                showEntity ->
                                        new ShowListItemRow(
                                                showEntity.getId(),
                                                showEntity.getTitle(),
                                                showEntity.getSubTitle(),
                                                showCardImagePathConverter.toCardImage(
                                                        showEntity.getImage()),
                                                genreNamesByShowId.getOrDefault(
                                                        showEntity.getId(), List.of()),
                                                showEntity.getStartDate(),
                                                showEntity.getEndDate(),
                                                showEntity.getViewCount(),
                                                showEntity.getDisplaySaleType(),
                                                showEntity.getDisplaySaleStartsAt(),
                                                showEntity.getDisplaySaleEndsAt(),
                                                showEntity.getCreatedAt(),
                                                showEntity.getVenueId()))
                        .toList());
    }

    private List<SaleOpeningSoonDetailRow> fetchSaleOpeningSoonDetailRows(
            final List<Long> showIds, final OrderSpecifier<?>[] orderSpecifiers) {
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
                        .where(show.id.in(showIds))
                        .orderBy(orderSpecifiers)
                        .fetch();

        return rows.stream().map(this::toSaleOpeningSoonDetailRow).toList();
    }

    private List<ShowSearchItemRow> fetchShowSearchRows(
            final List<Long> showIds, final OrderSpecifier<?>[] orderSpecifiers) {
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
                        .where(show.id.in(showIds))
                        .orderBy(orderSpecifiers)
                        .fetch();

        return rows.stream().map(this::toShowSearchItemRow).toList();
    }

    private List<Long> extractShowIds(final List<Tuple> rows) {
        return rows.stream().map(tuple -> tuple.get(show.id)).toList();
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

    private Map<Long, List<String>> fetchGenreNamesByShowId(final List<Long> showIds) {
        final List<Tuple> genreTuples =
                queryFactory
                        .select(show.id, genre.name)
                        .from(show)
                        .leftJoin(showGenre)
                        .on(showGenre.showId.eq(show.id))
                        .leftJoin(genre)
                        .on(showGenre.genreId.eq(genre.id))
                        .where(show.id.in(showIds))
                        .fetch();

        final Map<Long, List<String>> genreNamesByShowId = new LinkedHashMap<>();
        for (Tuple tuple : genreTuples) {
            final Long showId = required(tuple, show.id);
            final String genreName = tuple.get(genre.name);
            if (genreName != null) {
                genreNamesByShowId.computeIfAbsent(showId, key -> new ArrayList<>()).add(genreName);
            }
        }
        return genreNamesByShowId;
    }
}
