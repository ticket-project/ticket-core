package com.ticket.show.persistence;

import static com.ticket.show.domain.QCategory.category;
import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.performance.QPerformance.performance;
import static com.ticket.show.domain.performance.QPerformanceGrade.performanceGrade;
import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.domain.show.QShowGenre.showGenre;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.DisplaySaleWindow;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.Show;
import com.ticket.show.usecase.GetShowDetailUseCase.PriceSummary;
import com.ticket.show.usecase.SaleOpeningSoonSearchParam;
import com.ticket.show.usecase.ShowCursor;
import com.ticket.show.usecase.ShowListParam;
import com.ticket.show.usecase.ShowSearchCriteria;
import com.ticket.show.usecase.ShowSort;

import lombok.RequiredArgsConstructor;

/**
 * show 자기 DB의 읽기 전용 조회다 — 공연 목록·검색·오픈 예정·상세 가격 요약이 한 곳에 있다. show 자기 DB만 본다: 조회 결과는 {@code Show} 엔티티나 그 조각이고, venue 표시값
 * 조합·이미지 경로 변환·최종 응답 조립은 이 조회를 부르는 use case(application)가 한다.
 *
 * <p>밖으로 내보내는 것은 show 자기 엔티티와 타입 커서 위치뿐이다. Spring Data 타입과 HTTP 커서 문자열은 이 경계를 넘지 않는다.
 *
 * <p>지역 조건은 {@code venueIds}로 이미 해석돼 들어온다(use case가 {@code VenueLookupApi}로 해석한다) — {@code null}과 빈 집합의 차이는
 * {@link #venueIdIn}에 적었다.
 *
 * <p>정렬 정의·마감 판정 시각·커서 비교 규칙은 이 클래스 안에 한 벌만 둔다. 정렬이 마감 여부를 따로 판단하면 필터 결과와 정렬 결과가 어긋난다(TD-12).
 */
@Repository
@RequiredArgsConstructor
public class ShowQuerydslRepository {
    private final JPAQueryFactory queryFactory;
    private final Clock clock;

    // 공연 목록 · 검색 ----------------------------------------------------------

    public CursorPage<Show, ShowCursor> findAllBySearch(
            final ShowListParam param, final @Nullable Set<Long> venueIds, final int size, final ShowSort sort) {
        final SortOrder sortOrder = resolveSortOrder(sort, param.getCursor());
        final BooleanBuilder where = mainListCondition(param, venueIds, sortOrder);

        return findCursorPage(size, param.getCursor(), where, sortOrder);
    }

    /**
     * 상단 최신 공연 배너다. 전체 목록의 최신순과 같은 순서를 쓴다 — <b>마감되지 않은 공연 먼저, 등록일 내림차순, 등록일이 같으면 id 내림차순</b>. 배너와 목록이 다른 순서를 쓰면 같은 화면에서
     * "최신"의 의미가 둘이 된다.
     */
    public List<Show> findLatestShows(final String categoryCode, final int limit) {
        final SortOrder sortOrder = resolveSortOrder(ShowSort.LATEST);
        final List<Long> showIds = queryFactory
                .select(show.id)
                .from(show)
                .leftJoin(showGenre)
                .on(showGenre.showId.eq(show.id))
                .leftJoin(genre)
                .on(showGenre.genreId.eq(genre.id))
                .leftJoin(category)
                .on(genre.categoryId.eq(category.id))
                .where(categoryCodeEq(categoryCode))
                // DISTINCT 대신 GROUP BY인 이유는 fetchShowPageRows와 같다. ORDER BY의 마감 여부
                // CASE가 보는 판매 창 두 컬럼까지 GROUP BY에 넣는다 -- Oracle은 GROUP BY에 없는
                // 컬럼을 ORDER BY에서 쓰면 ORA-00979로 거절한다(H2는 show.id가 PK라는 함수 종속을
                // 알아서 통과시켜 이 차이가 테스트에 잡히지 않는다). show.id가 PK라 추가한 두 컬럼은
                // 함수 종속이고, 묶이는 행 자체는 달라지지 않는다.
                .groupBy(show.id, show.createdAt, show.displaySaleWindow.startsAt, show.displaySaleWindow.endsAt)
                .orderBy(orderSpecifiers(sortOrder))
                .limit(limit)
                .fetch();

        return findShowsInIdOrder(showIds);
    }

    public List<Show> findSaleOpeningSoonSummaries(final String categoryCode, final int limit) {
        final List<Long> showIds = queryFactory
                // SELECT DISTINCT는 ORDER BY에 쓴 식이 select 목록에 그대로 있어야 한다(H2).
                .select(show.id, show.displaySaleWindow.startsAt)
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
                .fetch()
                .stream()
                .map(tuple -> required(tuple, show.id))
                .toList();

        return findShowsInIdOrder(showIds);
    }

    public CursorPage<Show, ShowCursor> findSaleOpeningSoonPage(
            final SaleOpeningSoonSearchParam param,
            final @Nullable Set<Long> venueIds,
            final int size,
            final ShowSort sort) {
        final SortOrder sortOrder = resolveSortOrder(sort, param.getCursor());
        final BooleanBuilder where = saleOpeningSoonCondition(param, venueIds);

        return findCursorPage(size, param.getCursor(), where, sortOrder);
    }

    public CursorPage<Show, ShowCursor> searchShows(
            final ShowSearchCriteria criteria,
            final @Nullable Set<Long> venueIds,
            final int size,
            final ShowSort sort) {
        final SortOrder sortOrder = resolveSortOrder(sort, criteria.getCursor());
        final BooleanBuilder where = searchCondition(criteria, venueIds, sortOrder);

        return findCursorPage(size, criteria.getCursor(), where, sortOrder);
    }

    public long countSearchShows(final ShowSearchCriteria criteria, final @Nullable Set<Long> venueIds) {
        final BooleanBuilder where = searchCondition(criteria, venueIds, null);
        final Long count = queryFactory
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
            final ShowListParam param, final @Nullable Set<Long> venueIds, final SortOrder sortOrder) {
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
     * <p>판매 표시 상태 판정 시각과 공연 임박순의 기준 날짜는 <b>같은 now</b>를 쓴다. 집계는 정렬을 모르므로 {@code sortOrder}가 null로 들어오고, 그러면 공연 임박순 조건이
     * 붙지 않는다.
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

        where.and(saleDisplayStatusCondition(criteria.getSaleDisplayStatus(), now));
        appendShowStartApproachingCondition(where, sortOrder, now.toLocalDate());
        return where;
    }

    /**
     * 지역 조건은 application이 이미 venueId로 해석해 넘긴다.
     *
     * <p><b>{@code null}과 빈 집합을 같게 다루면 안 된다.</b> null은 지역 조건이 없다는 뜻이라 조건을 걸지 않고, 공연장이 없는 공연까지 전부 나온다. 빈 집합은 조건은 있는데 그
     * 지역에 공연장이 없다는 뜻이고, Querydsl이 {@code in(빈 컬렉션)}을 거짓 조건으로 직렬화해 결과가 0건이 된다 — 이것을 "조건 없음"으로 되돌리면 "그 지역에 공연장이 없다"가 "전체
     * 목록"으로 조용히 바뀐다.
     */
    private static @Nullable BooleanExpression venueIdIn(final @Nullable Set<Long> venueIds) {
        return venueIds == null ? null : show.venueId.in(venueIds);
    }

    private static void appendShowStartApproachingCondition(
            final BooleanBuilder where, final @Nullable SortOrder sortOrder, final LocalDate today) {
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

    // 판매 표시 상태 조건 --------------------------------------------------------
    //
    // SaleDisplayStatus 판정의 유일한 원본은 DisplaySaleWindow.statusAt(now)다. 여기서는 그 판정과 같은
    // 결론을 내는 Querydsl 조건만 만든다 -- null 창(시작·종료 중 하나라도 없음)은 CLOSED로 본다
    // (TD-12: 예전에는 이 null 처리가 도메인 판정과 달라서 필터 결과가 어긋났다).

    /**
     * 마감(={@code CLOSED})이면 1, 아니면 0. 최신순 정렬에서 마감된 공연을 뒤로 보내는 데 쓴다 (ORDER BY 이 값 ASC -> 마감되지 않은 공연이 먼저).
     *
     * <p>판정은 {@link #saleDisplayStatusCondition}의 {@code CLOSED}와 같은 식이다.
     */
    private static NumberExpression<Integer> saleClosedRank(final LocalDateTime now) {
        return new CaseBuilder()
                .when(saleDisplayStatusCondition(SaleDisplayStatus.CLOSED, now))
                .then(1)
                .otherwise(0);
    }

    /** 상태 필터가 지정되지 않으면(= {@code null}) 조건 없이 전체를 보도록 {@code null}을 돌려준다. */
    private static @Nullable BooleanExpression saleDisplayStatusCondition(
            final @Nullable SaleDisplayStatus saleDisplayStatus, final LocalDateTime now) {
        if (saleDisplayStatus == null) {
            return null;
        }

        final var startsAt = show.displaySaleWindow.startsAt;
        final var endsAt = show.displaySaleWindow.endsAt;

        return switch (saleDisplayStatus) {
            case BEFORE_OPEN -> startsAt.isNotNull().and(endsAt.isNotNull()).and(startsAt.gt(now));
            case ON_SALE ->
                startsAt.isNotNull()
                        .and(endsAt.isNotNull())
                        .and(startsAt.loe(now))
                        .and(endsAt.goe(now));
            case CLOSED -> startsAt.isNull().or(endsAt.isNull()).or(endsAt.lt(now));
        };
    }

    // 정렬 -------------------------------------------------------------------
    //
    // app이 이미 파싱한 ShowSort를 Querydsl 정렬 방향과 OrderSpecifier로 바꾼다.
    //
    // ShowSort.LATEST만 정렬 키가 셋이다 -- 마감되지 않은 공연 먼저, 그 안에서 등록일 내림차순,
    // 등록일이 같으면 id 내림차순. 마감 판정에는 시각이 필요하고, 그 시각은 페이지 사이에 흔들리면
    // 안 되므로(ShowCursor 참고) SortOrder가 들고 다닌다.

    /** @param saleClosedEvaluatedAt 마감 여부 판정 시각. {@link ShowSort#LATEST}에서만 값이 있다. */
    private record SortOrder(
            ShowSort key,
            Sort.Direction direction,
            @Nullable LocalDateTime saleClosedEvaluatedAt) {}

    /**
     * 첫 페이지는 현재 시각으로, 이어지는 페이지는 커서에 적힌 시각으로 마감 여부를 판정한다.
     *
     * @throws InvalidRequestException 최신순인데 커서에 판정 시각이 없거나 형식이 틀릴 때. 정렬 규칙이 바뀌기 전에 발급된 커서가 여기에 걸린다 — 조용히 섞인 순서를 내놓는 것보다
     *     낫다.
     */
    private SortOrder resolveSortOrder(final ShowSort sort, final @Nullable ShowCursor cursor) {
        final Sort.Direction direction =
                switch (sort) {
                    case POPULAR, LATEST -> Sort.Direction.DESC;
                    case SHOW_START_APPROACHING, SALE_START_APPROACHING -> Sort.Direction.ASC;
                };
        return new SortOrder(sort, direction, resolveSaleClosedEvaluatedAt(sort, cursor));
    }

    private SortOrder resolveSortOrder(final ShowSort sort) {
        return resolveSortOrder(sort, null);
    }

    /** ORDER BY에 그대로 넘길 정렬 키 전체다. 페이지 조회와 결과 재조회가 같은 배열을 쓴다. */
    private OrderSpecifier<?>[] orderSpecifiers(final SortOrder sortOrder) {
        if (ShowSort.LATEST.equals(sortOrder.key())) {
            return new OrderSpecifier<?>[] {saleClosedRank(sortOrder).asc(), show.createdAt.desc(), show.id.desc()};
        }
        return new OrderSpecifier<?>[] {primaryOrderSpecifier(sortOrder), tieBreakerOrder(sortOrder)};
    }

    private static OrderSpecifier<?> primaryOrderSpecifier(final SortOrder sortOrder) {
        return switch (sortOrder.key()) {
            case POPULAR -> show.viewCount.desc();
            case LATEST -> show.createdAt.desc();
            case SHOW_START_APPROACHING -> show.startDate.asc();
            case SALE_START_APPROACHING -> show.displaySaleWindow.startsAt.asc();
        };
    }

    private static OrderSpecifier<Long> tieBreakerOrder(final SortOrder sortOrder) {
        return sortOrder.direction().isAscending() ? show.id.asc() : show.id.desc();
    }

    /** 최신순의 첫 정렬 키. 커서 조건도 같은 식을 써야 순서와 페이지 경계가 맞는다. */
    private static NumberExpression<Integer> saleClosedRank(final SortOrder sortOrder) {
        final LocalDateTime evaluatedAt = sortOrder.saleClosedEvaluatedAt();
        if (evaluatedAt == null) {
            throw new IllegalStateException("마감 여부 판정 시각이 없습니다. 최신순이 아닌 정렬에서 호출했습니다: " + sortOrder.key());
        }
        return saleClosedRank(evaluatedAt);
    }

    private @Nullable LocalDateTime resolveSaleClosedEvaluatedAt(
            final ShowSort sort, final @Nullable ShowCursor cursor) {
        if (!ShowSort.LATEST.equals(sort)) {
            return null;
        }
        if (cursor == null) {
            return LocalDateTime.now(clock);
        }
        if (cursor.evaluatedAt() == null || cursor.evaluatedAt().isBlank()) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
        try {
            return LocalDateTime.parse(cursor.evaluatedAt());
        } catch (final DateTimeParseException exception) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
    }

    // 커서 -------------------------------------------------------------------
    //
    // 커서 위치를 SQL 조건으로 바꾸고, 마지막 행에서 다음 커서 위치를 만든다.
    //
    // 커서의 wire 표현(Base64 문자열)은 com.ticket.show.endpoint.cursor.ShowCursorCodec이 소유한다.
    // 여기서는 타입 값만 다룬다.
    //
    // ShowSort.LATEST는 정렬 키가 셋이라 조건도 셋이 겹친다. 조건은 ORDER BY와 같은 순서·같은
    // 방향이어야 페이지 사이에 중복·누락이 생기지 않는다.

    private static void applyCursor(
            final BooleanBuilder where, final @Nullable ShowCursor cursor, final SortOrder sortOrder) {
        if (cursor == null) {
            return;
        }
        try {
            validateCursorMatchesRequest(cursor, sortOrder);
            where.and(cursorCondition(cursor, sortOrder));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
    }

    private static ShowCursor buildNextPosition(final List<Tuple> rows, final int size, final SortOrder sortOrder) {
        final Tuple lastRow = rows.get(size - 1);
        // show.id는 이 projection에 항상 들어 있는 PK라 조회된 행에서는 값이 비어 있을 수 없다.
        final Long lastId = required(lastRow, show.id);
        final String lastValue = resolveLastValue(lastRow, sortOrder);
        if (!ShowSort.LATEST.equals(sortOrder.key())) {
            return new ShowCursor(sortOrder.key(), sortOrder.direction().name(), lastValue, lastId);
        }
        // 위에서 최신순이 아니면 이미 반환했고, 최신순 SortOrder는 판정 시각을 반드시 갖는다.
        final LocalDateTime evaluatedAt =
                Objects.requireNonNull(sortOrder.saleClosedEvaluatedAt(), "최신순 SortOrder에 마감 판정 시각이 없습니다.");
        return new ShowCursor(
                sortOrder.key(),
                sortOrder.direction().name(),
                lastValue,
                lastId,
                saleClosedRankOf(lastRow, evaluatedAt),
                evaluatedAt.toString());
    }

    /** 마지막 행의 마감 여부다. 판정은 {@link DisplaySaleWindow#statusAt}이 한다 — SQL 쪽 {@code CASE}와 같은 규칙이어야 커서 경계가 정렬과 어긋나지 않는다. */
    private static int saleClosedRankOf(final Tuple lastRow, final LocalDateTime evaluatedAt) {
        final DisplaySaleWindow window = new DisplaySaleWindow(
                lastRow.get(show.displaySaleWindow.startsAt), lastRow.get(show.displaySaleWindow.endsAt));
        return SaleDisplayStatus.CLOSED.equals(window.statusAt(evaluatedAt)) ? 1 : 0;
    }

    private static void validateCursorMatchesRequest(final ShowCursor cursor, final SortOrder sortOrder) {
        if (!sortOrder.key().equals(cursor.sort())) {
            throw new IllegalArgumentException("cursor.sort와 요청 sort가 일치하지 않습니다.");
        }
        if (!sortOrder.direction().name().equalsIgnoreCase(cursor.dir())) {
            throw new IllegalArgumentException("cursor.dir와 요청 dir가 일치하지 않습니다.");
        }
        if (cursor.lastId() == null) {
            throw new IllegalArgumentException("cursor.lastId가 없습니다.");
        }
        if (!StringUtils.hasText(cursor.lastValue())) {
            throw new IllegalArgumentException("cursor.lastValue가 없습니다.");
        }
        if (ShowSort.LATEST.equals(sortOrder.key()) && cursor.saleClosedRank() == null) {
            // 최신순 정렬이 마감 여부를 먼저 보기 전에 발급된 커서다. 그 커서로 이어 읽으면
            // 마감 그룹 경계를 무시하고 등록일만으로 잘라 중복·누락이 생긴다.
            throw new IllegalArgumentException("cursor.saleClosedRank가 없습니다.");
        }
    }

    private static BooleanExpression cursorCondition(final ShowCursor cursor, final SortOrder sortOrder) {
        final Long lastId = cursor.lastId();
        return switch (sortOrder.key()) {
            case POPULAR -> {
                final long last = Long.parseLong(cursor.lastValue());
                yield show.viewCount.lt(last).or(show.viewCount.eq(last).and(show.id.lt(lastId)));
            }
            case LATEST -> {
                final LocalDateTime last = LocalDateTime.parse(cursor.lastValue());
                final NumberExpression<Integer> rank = saleClosedRank(sortOrder);
                // LATEST 커서는 위 validate에서 saleClosedRank가 있는 것만 통과한다.
                final int lastRank = Objects.requireNonNull(cursor.saleClosedRank());
                final BooleanExpression afterWithinSameRank =
                        show.createdAt.lt(last).or(show.createdAt.eq(last).and(show.id.lt(lastId)));
                // ORDER BY: 마감 여부 ASC -> 등록일 DESC -> id DESC. 조건도 같은 순서로 겹친다.
                yield rank.gt(lastRank).or(rank.eq(lastRank).and(afterWithinSameRank));
            }
            case SHOW_START_APPROACHING -> {
                final LocalDate last = LocalDate.parse(cursor.lastValue());
                yield show.startDate.gt(last).or(show.startDate.eq(last).and(show.id.gt(lastId)));
            }
            case SALE_START_APPROACHING -> {
                final LocalDateTime last = LocalDateTime.parse(cursor.lastValue());
                yield show.displaySaleWindow
                        .startsAt
                        .gt(last)
                        .or(show.displaySaleWindow.startsAt.eq(last).and(show.id.gt(lastId)));
            }
        };
    }

    private static String resolveLastValue(final Tuple lastRow, final SortOrder sortOrder) {
        return switch (sortOrder.key()) {
            case POPULAR -> String.valueOf(required(lastRow, show.viewCount));
            case LATEST -> required(lastRow, show.createdAt).toString();
            // 이 정렬의 키가 startDate라 마지막 행에는 값이 있다.
            case SHOW_START_APPROACHING ->
                Objects.requireNonNull(lastRow.get(show.startDate)).toString();
            // 이 정렬은 판매 시작이 있는 행만 대상으로 하므로 마지막 행에도 값이 있다.
            case SALE_START_APPROACHING ->
                Objects.requireNonNull(lastRow.get(show.displaySaleWindow.startsAt))
                        .toString();
        };
    }

    // 페이지 조회 --------------------------------------------------------------

    /**
     * 커서 페이지 한 장을 읽는다. 1단계에서 정렬·커서로 id를 뽑고, 2단계에서 그 id로 본문을 다시 읽는다.
     *
     * <p><b>2단계에도 1단계와 같은 {@code orderSpecifiers}를 건다.</b> {@code IN (...)} 조회가 돌려주는 순서를 믿지 않기 위해서다. 1단계 query, 페이지 크기
     * 계산, 다음 커서는 여기서 한 번만 정의한다.
     */
    private CursorPage<Show, ShowCursor> findCursorPage(
            final int size, final @Nullable ShowCursor cursor, final BooleanBuilder where, final SortOrder sortOrder) {
        applyCursor(where, cursor, sortOrder);

        final OrderSpecifier<?>[] orderSpecifiers = orderSpecifiers(sortOrder);

        final List<Tuple> rows = fetchShowPageRows(where, orderSpecifiers, size);
        final List<Long> showIds = extractShowIds(rows);
        if (showIds.isEmpty()) {
            return CursorPage.empty();
        }

        final List<Show> results = new ArrayList<>(fetchShows(showIds, orderSpecifiers));
        final boolean hasNext = results.size() > size;
        final List<Show> pageResults = hasNext ? results.subList(0, size) : results;

        final ShowCursor nextPosition = hasNext ? buildNextPosition(rows, size, sortOrder) : null;

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
            // 다음 커서의 마감 여부 판정에 쓴다(buildNextPosition).
            show.displaySaleWindow.endsAt,
            show.viewCount
        };
    }

    /** 페이지 2단계다. 1단계가 고른 id의 {@code Show}를 1단계와 같은 정렬로 한 번에 읽는다. */
    private List<Show> fetchShows(final List<Long> showIds, final OrderSpecifier<?>[] orderSpecifiers) {
        return queryFactory
                .selectFrom(show)
                .where(show.id.in(showIds))
                .orderBy(orderSpecifiers)
                .fetch();
    }

    /**
     * 배너 2단계다. 정렬은 1단계가 이미 끝냈으므로 {@code IN (...)} 결과를 그 id 순서대로 다시 늘어놓는다 — 정렬식을 두 번 쓰지 않으니 동률의 순서가 두 query 사이에서 흔들리지
     * 않는다.
     */
    private List<Show> findShowsInIdOrder(final List<Long> showIds) {
        if (showIds.isEmpty()) {
            return List.of();
        }

        final Map<Long, Show> showsById = queryFactory.selectFrom(show).where(show.id.in(showIds)).fetch().stream()
                .collect(Collectors.toMap(Show::getId, showEntity -> showEntity));

        return showIds.stream().map(showsById::get).filter(Objects::nonNull).toList();
    }

    private List<Long> extractShowIds(final List<Tuple> rows) {
        return rows.stream().map(tuple -> tuple.get(show.id)).toList();
    }

    // 상세 조회 조각 ------------------------------------------------------------

    /**
     * ADR 0005: show-level 가격표는 없다. 이 show의 모든 Performance에 배정된 PerformanceGrade.price 중 최소/최대만 파생한다 — 대표 회차 하나의 가격을
     * show 전체 가격처럼 보여주지 않는다.
     */
    public @Nullable PriceSummary findPriceSummary(final Long showId) {
        final Tuple result = queryFactory
                .select(performanceGrade.price.min(), performanceGrade.price.max())
                .from(performanceGrade)
                .join(performanceGrade.performance, performance)
                .where(performance.showId.eq(showId))
                .fetchOne();
        if (result == null) {
            return null;
        }
        final var minPrice = result.get(performanceGrade.price.min());
        final var maxPrice = result.get(performanceGrade.price.max());
        if (minPrice == null || maxPrice == null) {
            return null;
        }
        return new PriceSummary(minPrice, maxPrice);
    }

    static <T> T required(final Tuple tuple, final Expression<T> column) {
        return Objects.requireNonNull(tuple.get(column), () -> column + "은 NOT NULL 컬럼이다");
    }
}
