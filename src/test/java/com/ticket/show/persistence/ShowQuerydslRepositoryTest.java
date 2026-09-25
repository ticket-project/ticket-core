package com.ticket.show.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.Category;
import com.ticket.show.domain.Genre;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowGenre;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.show.usecase.SaleOpeningSoonSearchParam;
import com.ticket.show.usecase.ShowCursor;
import com.ticket.show.usecase.ShowListParam;
import com.ticket.show.usecase.ShowSearchCriteria;
import com.ticket.show.usecase.ShowSort;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.domain.Region;
import com.ticket.venue.domain.Venue;
import com.ticket.venue.persistence.VenueRepositoryAdapter;
import com.ticket.venue.usecase.VenueLookupService;

/**
 * {@link ShowQuerydslRepository}의 실제 DB 조회 동작을 고정한다.
 *
 * <p>정렬 키·tie breaker, 커서 형식 검증, 최신순의 마감 판정(TD-12: null 창은 CLOSED)은 예전에 {@code QuerydslShowSortResolverTest} /
 * {@code QuerydslShowCursorConditionBuilderTest} / {@code SaleDisplayStatusPredicatesTest}가 조건식의 <b>형태</b>로 고정하던 것이다. 그
 * helper들이 이 Repository 안으로 흡수되면서 같은 행동을 <b>조회 결과</b>로 검증한다 — 조건식 문자열이 아니라 실제로 무엇이 나오고 무엇이 걸러지는지를 본다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = ShowQuerydslRepositoryTest.TestApplication.class)
@TestPropertySource(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:show-query-test;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.show-sql=false",
            // ModuleObservabilityAutoConfiguration이 기본으로(matchIfMissing=true) 활성화되어
            // ApplicationModulesRuntime을 즉시 요구한다. 이 좁은 슬라이스는 @SpringBootApplication
            // main class가 없어 그 런타임을 만들 수 없으므로 tracing 관측 자체를 끈다.
            "management.tracing.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                    + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                    + "org.redisson.spring.starter.RedissonAutoConfigurationV4,"
                    + "org.springframework.modulith.actuator.autoconfigure.ApplicationModulesEndpointConfiguration,"
                    + "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration"
        })
@Transactional
@Import({
    ShowQuerydslRepositoryTest.QuerydslTestConfig.class,
    ShowQuerydslRepositoryTest.TestConfig.class,
    ShowQuerydslRepositoryTest.AuditingTestConfig.class,
    ShowQuerydslRepository.class,
    ShowRepositoryAdapter.class,
    VenueRepositoryAdapter.class,
    VenueLookupService.class
})
@SuppressWarnings("NonAsciiCharacters")
class ShowQuerydslRepositoryTest {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ShowQuerydslRepository showQuerydslRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private VenueLookupApi venueLookupApi;

    private Venue seoulVenue;
    private Venue busanVenue;

    @BeforeEach
    void setUp() throws Exception {
        seoulVenue = persistVenue("Seoul Hall", Region.SEOUL);
        busanVenue = persistVenue("Busan Hall", Region.GYEONGSANG);

        persistShow(
                "Seoul Popular",
                300L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        persistShow(
                "Seoul Normal",
                120L,
                LocalDate.now().plusDays(10),
                seoulVenue,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().plusDays(8));
        persistShow(
                "Busan Hit",
                999L,
                LocalDate.now().plusDays(3),
                busanVenue,
                LocalDateTime.now().minusDays(3),
                LocalDateTime.now().plusDays(7));
        persistShow(
                "Closed Show",
                50L,
                LocalDate.now().minusDays(1),
                seoulVenue,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(2));

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * 목록 query는 show x showGenre x genre x category를 leftJoin하므로 장르가 여러 개면 행이 늘어난다. 그것을 GROUP BY가 정확히 하나로 접는다 -- 접지 못하면
     * 같은 공연이 목록에 여러 번 나오고 페이지 크기 계산도 어긋난다.
     *
     * <p>이 테스트가 없으면 {@code fetchShowPageRows}의 GROUP BY를 통째로 지워도 아무도 모른다.
     */
    @Test
    void 공연에_장르가_여러_개여도_목록에_한_번만_나온다() {
        final Show seoulPopular = findShowByTitle("Seoul Popular");
        attachGenres(seoulPopular, "뮤지컬", "연극", "콘서트");
        entityManager.flush();
        entityManager.clear();

        // 페이지 크기를 2로 잡는 것이 핵심이다. 1단계 query는 size + 1 = 3건만 읽으므로, 장르 조인으로
        // 늘어난 행이 접히지 않으면 그 3건이 전부 "Seoul Popular" 한 공연으로 채워져 두 번째 공연이
        // 페이지에서 밀려난다. size를 크게 잡으면 2단계의 IN 조회가 중복을 흡수해 버려 이 회귀를 놓친다.
        final CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 2, ShowSort.POPULAR);

        assertThat(result.items()).extracting(Show::getTitle).containsExactly("Seoul Popular", "Seoul Normal");
        assertThat(result.hasNext()).isTrue();
        assertThat(showRepository
                        .findGenreNamesByShowIds(List.of(seoulPopular.getId()))
                        .get(seoulPopular.getId()))
                .containsExactlyInAnyOrder("뮤지컬", "연극", "콘서트");
    }

    /** 같은 이유로 count도 행이 아니라 공연 수를 세야 한다({@code countDistinct}). */
    @Test
    void 공연에_장르가_여러_개여도_집계는_공연_수를_센다() {
        final Show seoulPopular = findShowByTitle("Seoul Popular");
        attachGenres(seoulPopular, "뮤지컬", "연극", "콘서트");
        entityManager.flush();
        entityManager.clear();

        final long count = countSearchShows(ShowSearchCriteria.of(null, null, null, null, null, "SEOUL", null));

        assertThat(count).isEqualTo(3);
    }

    /**
     * 목록 조회는 2단계다 -- 1단계가 정렬·커서로 id를 뽑고 2단계가 그 id로 본문을 다시 읽는다. 2단계는 {@code IN (...)} 조회라 DB가 돌려주는 순서를 믿을 수 없어서, 1단계와
     * <b>같은 ORDER BY</b>를 다시 건다.
     *
     * <p>그래서 정렬 순서와 id 순서가 어긋나도록 데이터를 만든 뒤 순서를 확인한다. 등록 순서(id 오름차순)와 조회수 순서가 반대가 되게 배치했으므로, 2단계의 ORDER BY가 빠지면 이 단언이
     * 깨진다.
     */
    @Test
    void 두_단계_조회에서도_정렬_순서가_유지된다() {
        // 등록 순서: A(1) -> B(2) -> C(3). 조회수 순서는 그 반대다.
        persistShow(
                "Order A",
                10L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        persistShow(
                "Order B",
                20L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        persistShow(
                "Order C",
                30L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        entityManager.flush();
        entityManager.clear();

        final CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.POPULAR);

        assertThat(result.items())
                .extracting(Show::getTitle)
                .startsWith("Seoul Popular", "Seoul Normal", "Closed Show", "Order C", "Order B", "Order A");
    }

    /**
     * region에 해당하는 공연장이 하나도 없으면 결과가 없다.
     *
     * <p>{@code venueId} 조건이 빈 집합에도 {@code null}(조건 없음)이 아니라 {@code 1 = 2}에 해당하는 조건을 돌려주기 때문이다. 이것을 "빈 집합이면 조건 없음"으로
     * 바꾸면 필터가 통째로 사라져 <b>전체 목록이 나온다</b> — 조용히 틀리는 회귀라 목록과 집계 양쪽을 고정한다.
     */
    @Test
    void 지역에_공연장이_하나도_없으면_목록과_집계가_모두_비어_있다() {
        final CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "JEJU", null), 10, ShowSort.POPULAR);
        final long count = countSearchShows(ShowSearchCriteria.of(null, null, null, null, null, "JEJU", null));

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
        assertThat(count).isZero();
    }

    private Show findShowByTitle(final String title) {
        return entityManager
                .createQuery("select s from Show s where s.title = :title", Show.class)
                .setParameter("title", title)
                .getSingleResult();
    }

    @Test
    void 지역으로_필터링하고_인기순으로_공연을_조회한다() {
        ShowListParam param = new ShowListParam(null, null, "SEOUL", null);

        CursorPage<Show, ShowCursor> result = findAllBySearch(param, 10, ShowSort.POPULAR);
        List<Show> slice = result.items();

        assertThat(slice).extracting(Show::getTitle).containsExactly("Seoul Popular", "Seoul Normal", "Closed Show");
        assertThat(slice).extracting(Show::getViewCount).containsExactly(300L, 120L, 50L);
        assertThat(result.nextPosition()).isNull();
    }

    @Test
    void 커서를_전달하면_다음_페이지를_조회한다() {
        ShowListParam firstPageParam = new ShowListParam(null, null, "SEOUL", null);
        CursorPage<Show, ShowCursor> firstPage = findAllBySearch(firstPageParam, 1, ShowSort.POPULAR);

        ShowListParam secondPageParam = new ShowListParam(null, null, "SEOUL", firstPage.nextPosition());
        CursorPage<Show, ShowCursor> secondPage = findAllBySearch(secondPageParam, 1, ShowSort.POPULAR);

        assertThat(firstPage.items()).extracting(Show::getTitle).containsExactly("Seoul Popular");
        assertThat(firstPage.nextPosition()).isNotNull();
        assertThat(secondPage.items()).extracting(Show::getTitle).containsExactly("Seoul Normal");
    }

    @Test
    void 검색_조건에_맞는_공연만_집계한다() {
        ShowSearchCriteria request =
                new ShowSearchCriteria("Seoul", null, SaleDisplayStatus.ON_SALE, null, null, "SEOUL", null);

        long count = countSearchShows(request);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void 검색_api는_판매중인_서울_공연만_조회한다() {
        ShowSearchCriteria request =
                new ShowSearchCriteria("Seoul", null, SaleDisplayStatus.ON_SALE, null, null, "SEOUL", null);

        CursorPage<Show, ShowCursor> result = searchShows(request, 10, ShowSort.POPULAR);

        assertThat(result.items()).extracting(Show::getTitle).containsExactly("Seoul Popular", "Seoul Normal");
    }

    @Test
    void 최신순은_마감된_공연을_아무리_최근에_등록해도_뒤로_보낸다() {
        // 가장 최근에 등록된 공연이 마감된 공연이다. 그래도 예매 가능한 공연이 먼저 나와야 한다.
        setCreatedAt("Closed Show", LocalDateTime.now());
        setCreatedAt("Seoul Popular", LocalDateTime.now().minusDays(1));
        setCreatedAt("Seoul Normal", LocalDateTime.now().minusDays(2));

        CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.LATEST);

        assertThat(result.items())
                .extracting(Show::getTitle)
                .containsExactly("Seoul Popular", "Seoul Normal", "Closed Show");
    }

    @Test
    void 최신순은_마감되지_않은_그룹_안에서_등록일_내림차순이다() {
        setCreatedAt("Seoul Normal", LocalDateTime.now());
        setCreatedAt("Seoul Popular", LocalDateTime.now().minusDays(1));
        setCreatedAt("Closed Show", LocalDateTime.now().minusDays(5));

        CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.LATEST);

        assertThat(result.items())
                .extracting(Show::getTitle)
                .containsExactly("Seoul Normal", "Seoul Popular", "Closed Show");
    }

    @Test
    void 최신순은_등록일이_같으면_id_내림차순으로_안정적이다() {
        LocalDateTime sameInstant = LocalDateTime.now().minusHours(1);
        setCreatedAt("Seoul Popular", sameInstant);
        setCreatedAt("Seoul Normal", sameInstant);
        setCreatedAt("Closed Show", sameInstant);

        List<String> first =
                titlesOf(findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.LATEST));
        List<String> second =
                titlesOf(findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.LATEST));

        assertThat(first).as("같은 등록일이어도 순서가 흔들리지 않는다").isEqualTo(second);
        assertThat(first).endsWith("Closed Show");
        assertThat(idOf(first.get(0))).isGreaterThan(idOf(first.get(1)));
    }

    @Test
    void 최신순을_여러_페이지로_나눠_읽어도_중복이나_누락이_없다() {
        setCreatedAt("Closed Show", LocalDateTime.now());
        setCreatedAt("Seoul Popular", LocalDateTime.now().minusDays(1));
        setCreatedAt("Seoul Normal", LocalDateTime.now().minusDays(2));

        List<String> paged = new ArrayList<>();
        ShowCursor cursor = null;
        for (int page = 0; page < 5; page++) {
            CursorPage<Show, ShowCursor> result =
                    findAllBySearch(new ShowListParam(null, null, "SEOUL", cursor), 1, ShowSort.LATEST);
            paged.addAll(titlesOf(result));
            cursor = result.nextPosition();
            if (cursor == null) {
                break;
            }
        }

        assertThat(paged).as("한 장에 다 담아 읽은 결과와 같아야 한다").containsExactly("Seoul Popular", "Seoul Normal", "Closed Show");
        assertThat(paged).doesNotHaveDuplicates();
    }

    @Test
    void 상단_최신_공연_배너도_마감된_공연을_뒤로_보낸다() {
        setCreatedAt("Closed Show", LocalDateTime.now());
        setCreatedAt("Seoul Popular", LocalDateTime.now().minusDays(1));

        List<Show> rows = showQuerydslRepository.findLatestShows(null, 10);

        assertThat(rows).extracting(Show::getTitle).endsWith("Closed Show");
    }

    /**
     * 배너 query는 GROUP BY로 장르 조인의 중복을 접는데, ORDER BY의 마감 여부 CASE가 보는 판매 창 컬럼이 GROUP BY에 없으면 Oracle이 ORA-00979로 거절한다. H2는
     * {@code show.id}가 PK라는 함수 종속을 알아서 통과시키므로 이 테스트가 Oracle에서의 실패를 잡지는 못한다 — 여기서 고정하는 것은 <b>중복 없이 세 정렬 키가 그대로 적용된
     * 결과</b>이고, GROUP BY를 좁히면 최소한 그 결과가 깨지는지는 보인다.
     */
    @Test
    void 최신_공연_배너는_장르가_여러_개여도_공연을_한_번만_담는다() {
        attachGenres(findShowByTitle("Seoul Popular"), "뮤지컬", "연극", "콘서트");
        entityManager.flush();
        entityManager.clear();
        setCreatedAt("Seoul Popular", LocalDateTime.now());
        setCreatedAt("Seoul Normal", LocalDateTime.now().minusDays(1));
        setCreatedAt("Busan Hit", LocalDateTime.now().minusDays(2));
        // 마감 공연이 가장 최신이어도 맨 뒤다 -- 마감 여부가 등록일보다 먼저다.
        setCreatedAt("Closed Show", LocalDateTime.now().plusDays(1));

        List<Show> rows = showQuerydslRepository.findLatestShows(null, 10);

        assertThat(rows)
                .extracting(Show::getTitle)
                .containsExactly("Seoul Popular", "Seoul Normal", "Busan Hit", "Closed Show");
        assertThat(rows).doesNotHaveDuplicates();
    }

    @Test
    void 최신_공연_배너는_등록일이_같으면_id_내림차순으로_정렬한다() {
        LocalDateTime sameMoment = LocalDateTime.now();
        setCreatedAt("Seoul Popular", sameMoment);
        setCreatedAt("Seoul Normal", sameMoment);
        setCreatedAt("Busan Hit", sameMoment);
        setCreatedAt("Closed Show", sameMoment);

        List<Show> rows = showQuerydslRepository.findLatestShows(null, 10);

        // 판매 중 셋은 나중에 만들어진 것(= 큰 id)이 먼저다. 마감 공연은 등록일이 같아도 맨 뒤다.
        assertThat(rows)
                .extracting(Show::getTitle)
                .containsExactly("Busan Hit", "Seoul Normal", "Seoul Popular", "Closed Show");
    }

    @Test
    void 최신_공연_배너는_카테고리로_거른다() {
        Show seoulNormal = findShowByTitle("Seoul Normal");
        attachGenres(seoulNormal, "뮤지컬", "연극");
        entityManager.flush();
        entityManager.clear();

        List<Show> rows = showQuerydslRepository.findLatestShows("CAT-" + seoulNormal.getId(), 10);

        assertThat(rows).extracting(Show::getTitle).containsExactly("Seoul Normal");
    }

    @Test
    void 인기순은_마감_여부를_정렬에_넣지_않는다() {
        // 사용자가 명시적으로 고른 정렬의 의미는 바꾸지 않는다.
        setCreatedAt("Closed Show", LocalDateTime.now());

        CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.POPULAR);

        assertThat(result.items())
                .extracting(Show::getTitle)
                .containsExactly("Seoul Popular", "Seoul Normal", "Closed Show");
    }

    @Test
    void 최신순도_지역_필터와_함께_동작한다() {
        setCreatedAt("Busan Hit", LocalDateTime.now());
        setCreatedAt("Seoul Popular", LocalDateTime.now().minusDays(1));

        CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "GYEONGSANG", null), 10, ShowSort.LATEST);

        assertThat(result.items()).extracting(Show::getTitle).containsExactly("Busan Hit");
    }

    @Test
    void 조건에_맞는_공연이_없으면_빈_슬라이스를_반환한다() {
        ShowListParam param = new ShowListParam(null, null, "JEOLLA", null);

        CursorPage<Show, ShowCursor> result = findAllBySearch(param, 10, ShowSort.POPULAR);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
    }

    // 정렬 키와 tie breaker ------------------------------------------------------
    //
    // 옛 QuerydslShowSortResolverTest는 OrderSpecifier 배열의 방향만 봤다. 여기서는 같은 규칙을
    // 실제 조회 결과 순서로 고정한다.

    /** 공연 임박순은 공연일 오름차순이고, 같은 날이면 id 오름차순이다. */
    @Test
    void 공연_임박순은_공연일_오름차순이고_같은_날이면_id_오름차순이다() {
        persistShow(
                "Tie A",
                10L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        persistShow(
                "Tie B",
                20L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        entityManager.flush();
        entityManager.clear();

        CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.SHOW_START_APPROACHING);

        // 같은 공연일(오늘 +5) 안에서는 등록 순서(id 오름차순)로 이어진다.
        assertThat(result.items())
                .extracting(Show::getTitle)
                .containsExactly("Seoul Popular", "Tie A", "Tie B", "Seoul Normal");
    }

    /** 인기순은 조회수 내림차순이고, 조회수가 같으면 id 내림차순이다. */
    @Test
    void 인기순은_조회수가_같으면_id_내림차순이다() {
        persistShow(
                "Same A",
                777L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        persistShow(
                "Same B",
                777L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(10));
        entityManager.flush();
        entityManager.clear();

        CursorPage<Show, ShowCursor> result =
                findAllBySearch(new ShowListParam(null, null, "SEOUL", null), 10, ShowSort.POPULAR);

        assertThat(titlesOf(result)).startsWith("Same B", "Same A");
    }

    // 커서 형식 검증 -------------------------------------------------------------
    //
    // 옛 QuerydslShowCursorConditionBuilderTest / QuerydslShowSortResolverTest가 helper를 직접
    // 불러 고정하던 거부 규칙이다. 지금은 조회 입구에서 같은 예외가 나오는지 본다.

    @Test
    void cursor의_sort가_요청과_다르면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(
                ShowSort.LATEST,
                "DESC",
                LocalDateTime.now().toString(),
                1L,
                0,
                LocalDateTime.now().toString());

        assertThatThrownBy(() -> findAllBySearch(seoulParam(cursor), 10, ShowSort.POPULAR))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_dir가_요청과_다르면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "ASC", "10", 1L);

        assertThatThrownBy(() -> findAllBySearch(seoulParam(cursor), 10, ShowSort.POPULAR))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_lastId가_없으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", "10", null);

        assertThatThrownBy(() -> findAllBySearch(seoulParam(cursor), 10, ShowSort.POPULAR))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_lastValue가_없으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", " ", 1L);

        assertThatThrownBy(() -> findAllBySearch(seoulParam(cursor), 10, ShowSort.POPULAR))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cursor의_lastValue가_정렬형식과_맞지_않으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor = new ShowCursor(
                ShowSort.LATEST,
                "DESC",
                "not-a-date",
                1L,
                0,
                LocalDateTime.now().toString());

        assertThatThrownBy(() -> findAllBySearch(seoulParam(cursor), 10, ShowSort.LATEST))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 최신순_커서에_판정_시각이_없으면_INVALID_INPUT_예외를_던진다() {
        // 정렬 규칙이 바뀌기 전에 발급된 커서다. 조용히 섞인 순서를 내놓는 것보다 거부가 낫다.
        ShowCursor legacyCursor =
                new ShowCursor(ShowSort.LATEST, "DESC", LocalDateTime.now().toString(), 1L);

        assertThatThrownBy(() -> findAllBySearch(seoulParam(legacyCursor), 10, ShowSort.LATEST))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 최신순_커서의_판정_시각_형식이_틀리면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor =
                new ShowCursor(ShowSort.LATEST, "DESC", LocalDateTime.now().toString(), 1L, 0, "not-a-datetime");

        assertThatThrownBy(() -> findAllBySearch(seoulParam(cursor), 10, ShowSort.LATEST))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 최신순_커서에_마감여부가_없으면_INVALID_INPUT_예외를_던진다() {
        // 최신순이 마감 여부를 먼저 보기 전에 발급된 커서다. 그대로 이어 읽으면 마감 그룹 경계를
        // 무시하고 등록일만으로 잘라 중복·누락이 생긴다.
        ShowCursor cursor = new ShowCursor(
                ShowSort.LATEST,
                "DESC",
                LocalDateTime.now().toString(),
                1L,
                null,
                LocalDateTime.now().toString());

        assertThatThrownBy(() -> findAllBySearch(seoulParam(cursor), 10, ShowSort.LATEST))
                .isInstanceOf(InvalidRequestException.class);
    }

    // 다음 커서에 담기는 값 --------------------------------------------------------

    @Test
    void 인기순_다음_커서는_마감여부와_판정_시각을_담지_않는다() {
        CursorPage<Show, ShowCursor> page = findAllBySearch(seoulParam(null), 1, ShowSort.POPULAR);

        assertThat(page.nextPosition()).isNotNull();
        assertThat(page.nextPosition().saleClosedRank()).isNull();
        assertThat(page.nextPosition().evaluatedAt()).isNull();
    }

    @Test
    void 최신순_다음_커서에_마감여부와_판정_시각을_담고_다음_페이지도_그_시각을_그대로_쓴다() {
        setCreatedAt("Seoul Popular", LocalDateTime.now());
        setCreatedAt("Seoul Normal", LocalDateTime.now().minusDays(1));
        setCreatedAt("Closed Show", LocalDateTime.now().minusDays(2));

        CursorPage<Show, ShowCursor> firstPage = findAllBySearch(seoulParam(null), 1, ShowSort.LATEST);

        assertThat(firstPage.nextPosition()).isNotNull();
        assertThat(firstPage.nextPosition().saleClosedRank()).isZero();
        assertThat(firstPage.nextPosition().evaluatedAt()).isNotNull();

        CursorPage<Show, ShowCursor> secondPage =
                findAllBySearch(seoulParam(firstPage.nextPosition()), 1, ShowSort.LATEST);

        // 페이지를 넘기는 사이 마감된 공연이 그룹을 옮기면 중복·누락이 생긴다. 시각을 고정한다.
        assertThat(secondPage.nextPosition().evaluatedAt())
                .isEqualTo(firstPage.nextPosition().evaluatedAt());
    }

    @Test
    void 판매기간이_끝난_공연은_다음_커서에서_마감으로_적힌다() {
        persistShow(
                "Closed Later",
                10L,
                LocalDate.now().plusDays(5),
                seoulVenue,
                LocalDateTime.now().minusDays(20),
                LocalDateTime.now().minusDays(15));
        entityManager.flush();
        entityManager.clear();

        CursorPage<Show, ShowCursor> result = searchShows(closedCriteria(), 1, ShowSort.LATEST);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextPosition().saleClosedRank()).isEqualTo(1);
    }

    /**
     * 표시 판매기간이 비어 있는 공연도 마감으로 본다 — {@code DisplaySaleWindow.statusAt}과 같은 규칙이다(TD-12). 필터 결과와 다음 커서 양쪽에서 같은 결론이 나와야 정렬
     * 경계가 어긋나지 않는다.
     */
    @Test
    void 표시_판매기간이_비어있는_공연도_마감으로_본다() {
        persistShow("No Window", 10L, LocalDate.now().plusDays(5), seoulVenue, null, null);
        entityManager.flush();
        entityManager.clear();
        setCreatedAt("No Window", LocalDateTime.now());

        CursorPage<Show, ShowCursor> result = searchShows(closedCriteria(), 1, ShowSort.LATEST);

        assertThat(result.items())
                .as("null 창은 CLOSED 필터에 걸린다")
                .extracting(Show::getTitle)
                .containsExactly("No Window");
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextPosition().saleClosedRank()).isEqualTo(1);
    }

    // 판매 표시 상태 필터 ---------------------------------------------------------

    @Test
    void 판매_표시_상태를_주지_않으면_상태로_거르지_않는다() {
        CursorPage<Show, ShowCursor> result =
                searchShows(new ShowSearchCriteria(null, null, null, null, null, "SEOUL", null), 10, ShowSort.POPULAR);

        assertThat(result.items())
                .extracting(Show::getTitle)
                .containsExactly("Seoul Popular", "Seoul Normal", "Closed Show");
    }

    @Test
    void 판매_시작_전_공연만_거른다() {
        persistShow(
                "Before Open",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(20));
        entityManager.flush();
        entityManager.clear();

        CursorPage<Show, ShowCursor> result = searchShows(
                new ShowSearchCriteria(null, null, SaleDisplayStatus.BEFORE_OPEN, null, null, null, null),
                10,
                ShowSort.POPULAR);

        assertThat(result.items()).extracting(Show::getTitle).containsExactly("Before Open");
    }

    @Test
    void 판매가_끝난_공연만_거른다() {
        CursorPage<Show, ShowCursor> result = searchShows(closedCriteria(), 10, ShowSort.POPULAR);

        assertThat(result.items()).extracting(Show::getTitle).containsExactly("Closed Show");
    }

    /** 등록일은 JPA auditing이 넣으므로 테스트에서 직접 못 정한다. 최신순 정렬은 이 값이 기준이라 네이티브 UPDATE로 고정한다. */
    private void setCreatedAt(final String title, final LocalDateTime createdAt) {
        entityManager
                .createNativeQuery("UPDATE SHOWS SET created_at = ?1 WHERE title = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, title)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private Long idOf(final String title) {
        return entityManager
                .createQuery("select s.id from Show s where s.title = :title", Long.class)
                .setParameter("title", title)
                .getSingleResult();
    }

    private static List<String> titlesOf(final CursorPage<Show, ShowCursor> page) {
        return page.items().stream().map(Show::getTitle).toList();
    }

    private static ShowListParam seoulParam(final @Nullable ShowCursor cursor) {
        return new ShowListParam(null, null, "SEOUL", cursor);
    }

    private static ShowSearchCriteria closedCriteria() {
        return new ShowSearchCriteria(null, null, SaleDisplayStatus.CLOSED, null, null, null, null);
    }

    // 판매 오픈 예정 경로 ------------------------------------------------------
    //
    // 아래 두 query는 그동안 DB 레벨 테스트가 없었다. 조건 조립을 adapter 안으로 들이기 전에
    // 지금 무엇을 보장하는지 먼저 고정한다.

    /** 오픈 예정 목록은 아직 판매가 시작되지 않은 공연만, 판매 시작이 이른 순서로 돌려준다. */
    @Test
    void 오픈_예정_목록은_판매_시작_전_공연만_이른_순서로_돌려준다() {
        persistShow(
                "Soon Later",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(9),
                LocalDateTime.now().plusDays(20));
        persistShow(
                "Soon Earlier",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(20));
        entityManager.flush();
        entityManager.clear();

        final CursorPage<Show, ShowCursor> result =
                findSaleOpeningSoonPage(saleOpeningSoon(null, null, null), 10, ShowSort.SALE_START_APPROACHING);

        // 이미 판매가 시작된 setUp의 네 공연은 빠진다.
        assertThat(result.items()).extracting(Show::getTitle).containsExactly("Soon Earlier", "Soon Later");
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
    }

    /** 오픈 예정 목록의 지역과 제목 필터가 실제로 걸린다. */
    @Test
    void 오픈_예정_목록은_지역과_제목으로_거른다() {
        persistShow(
                "Soon Seoul",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(20));
        persistShow(
                "Soon Busan",
                10L,
                LocalDate.now().plusDays(40),
                busanVenue,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(20));
        entityManager.flush();
        entityManager.clear();

        assertThat(findSaleOpeningSoonPage(saleOpeningSoon(null, null, "SEOUL"), 10, ShowSort.SALE_START_APPROACHING)
                        .items())
                .extracting(Show::getTitle)
                .containsExactly("Soon Seoul");

        assertThat(findSaleOpeningSoonPage(saleOpeningSoon(null, "busan", null), 10, ShowSort.SALE_START_APPROACHING)
                        .items())
                .extracting(Show::getTitle)
                .containsExactly("Soon Busan");
    }

    /** 판매 시작 범위 필터는 열린 구간으로 건다. */
    @Test
    void 오픈_예정_목록은_판매_시작_범위로_거른다() {
        persistShow(
                "Soon Near",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(20));
        persistShow(
                "Soon Far",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(40));
        entityManager.flush();
        entityManager.clear();

        final CursorPage<Show, ShowCursor> result = findSaleOpeningSoonPage(
                new SaleOpeningSoonSearchParam(
                        null, null, null, null, LocalDateTime.now().plusDays(10), null, null, null),
                10,
                ShowSort.SALE_START_APPROACHING);

        assertThat(result.items()).extracting(Show::getTitle).containsExactly("Soon Near");
    }

    /** 오픈 예정 요약은 {@code distinct}로 장르 조인 중복을 제거한다. GROUP BY와 교체하지 않고 현재 동작을 고정한다({@code docs/coding-guidelines.md}). */
    @Test
    void 오픈_예정_요약은_장르가_여러_개여도_한_번만_나온다() {
        final Show soon = persistShow(
                "Soon Summary",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(20));
        entityManager.flush();
        attachGenres(soon, "뮤지컬", "연극", "콘서트");
        entityManager.flush();
        entityManager.clear();

        assertThat(showQuerydslRepository.findSaleOpeningSoonSummaries(null, 10))
                .extracting(Show::getTitle)
                .containsExactly("Soon Summary");
    }

    // 오름차순 정렬과 커서 ------------------------------------------------------

    /** 커서 왕복은 그동안 LATEST와 POPULAR(둘 다 DESC)만 확인했다. ASC 정렬은 커서 비교가 반대 부등호로 뒤집히므로 별도 경로다. 한 건씩 끝까지 넘기며 중복도 누락도 없는지 본다. */
    @Test
    void 판매_시작_임박순_커서를_끝까지_넘겨도_중복도_누락도_없다() {
        persistShow(
                "Soon C",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(20));
        persistShow(
                "Soon A",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(3),
                LocalDateTime.now().plusDays(20));
        persistShow(
                "Soon B",
                10L,
                LocalDate.now().plusDays(40),
                seoulVenue,
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(20));
        entityManager.flush();
        entityManager.clear();

        final List<String> visited = new ArrayList<>();
        ShowCursor cursor = null;
        for (int page = 0; page < 5; page++) {
            final CursorPage<Show, ShowCursor> result = findSaleOpeningSoonPage(
                    new SaleOpeningSoonSearchParam(null, null, null, null, null, null, null, cursor),
                    1,
                    ShowSort.SALE_START_APPROACHING);
            result.items().stream().map(Show::getTitle).forEach(visited::add);
            if (!result.hasNext()) {
                break;
            }
            cursor = result.nextPosition();
        }

        assertThat(visited).containsExactly("Soon A", "Soon B", "Soon C");
    }

    /**
     * 공연 임박순 정렬은 <b>WHERE까지 바꾼다</b>. 이미 시작한 공연을 빼려고 startDate 하한을 더하는데, 집계는 정렬을 모르므로 그 조건이 붙지 않아 같은 검색의 목록 건수와 집계 건수가
     * 어긋난다.
     *
     * <p>이것은 <b>현재 동작</b>이고 이번 작업에서 고치지 않는다. 조건 조립을 옮기면서 이 비대칭을 잃지 않도록 먼저 고정한다.
     */
    @Test
    void 공연_임박순_정렬만_지난_공연을_빼고_집계는_빼지_않는다() {
        final ShowSearchCriteria criteria = ShowSearchCriteria.of(null, null, null, null, null, null, null);

        final CursorPage<Show, ShowCursor> approaching = searchShows(criteria, 10, ShowSort.SHOW_START_APPROACHING);
        final CursorPage<Show, ShowCursor> popular = searchShows(criteria, 10, ShowSort.POPULAR);

        // "Closed Show"는 startDate가 어제라 임박순에서만 빠진다.
        assertThat(approaching.items()).extracting(Show::getTitle).doesNotContain("Closed Show");
        assertThat(popular.items()).extracting(Show::getTitle).contains("Closed Show");

        assertThat(approaching.items()).hasSize(3);
        assertThat(countSearchShows(criteria)).isEqualTo(4);
    }

    private SaleOpeningSoonSearchParam saleOpeningSoon(final String category, final String title, final String region) {
        return new SaleOpeningSoonSearchParam(category, title, region, null, null, null, null, null);
    }

    // 지역 조건 해석은 application의 몫이다. 이 테스트도 use case와 같은 순서로 -- 지역을 venueId로 먼저
    // 해석한 뒤 Repository를 부른다 -- 조합해야 실제 동작과 같은 것을 검증한다.

    private CursorPage<Show, ShowCursor> findAllBySearch(
            final ShowListParam param, final int size, final ShowSort sort) {
        return showQuerydslRepository.findAllBySearch(param, venueIdsOf(param.getRegion()), size, sort);
    }

    private CursorPage<Show, ShowCursor> searchShows(
            final ShowSearchCriteria criteria, final int size, final ShowSort sort) {
        return showQuerydslRepository.searchShows(criteria, venueIdsOf(criteria.getRegion()), size, sort);
    }

    private long countSearchShows(final ShowSearchCriteria criteria) {
        return showQuerydslRepository.countSearchShows(criteria, venueIdsOf(criteria.getRegion()));
    }

    private CursorPage<Show, ShowCursor> findSaleOpeningSoonPage(
            final SaleOpeningSoonSearchParam param, final int size, final ShowSort sort) {
        return showQuerydslRepository.findSaleOpeningSoonPage(param, venueIdsOf(param.getRegion()), size, sort);
    }

    /** {@code null}(지역 조건 없음)과 빈 집합(그 지역에 공연장 없음)을 구분해 넘긴다 — use case와 같은 규칙이다. */
    private @Nullable Set<Long> venueIdsOf(final @Nullable String regionCode) {
        return regionCode == null ? null : venueLookupApi.findIdsByRegion(regionCode);
    }

    private Venue persistVenue(final String name, final Region region) throws Exception {
        Venue venue = Venue.create(
                name,
                name + " address",
                region,
                "detail",
                "12345",
                BigDecimal.valueOf(37.0),
                BigDecimal.valueOf(127.0),
                "010-0000-0000",
                "https://example.com/venue.png",
                1000,
                800,
                10.0,
                2.0,
                2.0);
        entityManager.persist(venue);
        return venue;
    }

    private Show persistShow(
            final String title,
            final long viewCount,
            final LocalDate startDate,
            final Venue venue,
            final LocalDateTime saleStartDate,
            final LocalDateTime saleEndDate) {
        Show show = new Show(
                title,
                title + " subtitle",
                title + " description",
                startDate,
                startDate.plusDays(30),
                viewCount,
                SaleType.GENERAL,
                saleStartDate,
                saleEndDate,
                "https://example.com/show.png",
                venue == null ? null : venue.getId(),
                null,
                120);
        entityManager.persist(show);
        return show;
    }

    /** 한 공연에 장르를 여러 개 붙인다. 목록 query가 장르 join으로 늘어난 행을 접는지 확인하기 위한 fixture다. */
    private void attachGenres(final Show show, final String... genreNames) {
        final Category category = Category.of("CAT-" + show.getId(), "카테고리");
        entityManager.persist(category);
        for (final String genreName : genreNames) {
            final Genre genre = new Genre("G-" + show.getId() + "-" + genreName, genreName, category.getId());
            entityManager.persist(genre);
            entityManager.persist(new ShowGenre(show.getId(), genre.getId()));
        }
    }

    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }

    static class QuerydslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(final EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @EnableJpaAuditing
    static class AuditingTestConfig {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> java.util.Optional.of("test-auditor");
        }
    }

    // @TestComponent는 이 클래스를 다른 @SpringBootTest 컨텍스트(TicketApplication 등)의
    // component scan에서 제외시킨다. 단일 프로젝트로 합쳐지며 같은 com.ticket 패키지 트리에
    // 놓이게 된 이 테스트 전용 설정이 실제 앱의 component scan에 섞여 들어가는 것을 막는다.
    // @TestConfiguration을 쓰면 안 된다 — SpringBootTestContextBootstrapper가 classes=...로
    // 명시한 설정을 전부 @TestConfiguration으로 보고 "명시하지 않은 것"처럼 취급해, 패키지를
    // 거슬러 올라가며 다른 @SpringBootConfiguration을 찾아 잘못 병합해버린다.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @TestComponent
    @EntityScan(basePackages = {"com.ticket.show", "com.ticket.venue", "com.ticket.member", "com.ticket.booking"})
    // 이 슬라이스가 @Import하는 조회 Repository가 쓰는 Spring Data 인터페이스만 올린다. 명시하지 않으면
    // auto-configuration package(=이 클래스의 package)만 스캔해 venue 쪽 인터페이스가 빠진다.
    @EnableJpaRepositories(basePackages = {"com.ticket.show", "com.ticket.venue"})
    @Import({TestConfig.class, AuditingTestConfig.class})
    static class TestApplication {}
}
