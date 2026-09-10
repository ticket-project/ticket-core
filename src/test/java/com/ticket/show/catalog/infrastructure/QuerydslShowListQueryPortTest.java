package com.ticket.show.catalog.infrastructure;

import com.ticket.show.catalog.application.port.ShowListQueryPort;

import com.ticket.show.catalog.application.port.ShowListQueryPort;
import com.ticket.show.catalog.application.ShowSort;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.show.catalog.domain.SaleDisplayStatus;
import com.ticket.show.catalog.domain.ShowCardImagePathConverter;
import com.ticket.venue.Region;
import com.ticket.show.catalog.domain.SaleType;
import com.ticket.show.catalog.domain.Show;
import com.ticket.show.catalog.application.ShowListItemRow;
import com.ticket.show.catalog.application.ShowParam;
import com.ticket.show.catalog.application.ShowSearchCriteria;
import com.ticket.show.catalog.application.ShowSearchItemRow;
import com.ticket.venue.facility.domain.Venue;
import com.ticket.show.catalog.application.ShowCursor;
import com.ticket.shared.CursorPage;
import com.ticket.venue.facility.application.VenueLookupService;
import com.ticket.venue.seat.application.VenueSeatLookupService;
import com.ticket.venue.seat.infrastructure.QuerydslVenueSeatQueryPort;
import com.ticket.venue.facility.infrastructure.QuerydslVenueSummaryQueryPort;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = QuerydslShowListQueryPortTest.TestApplication.class)
@TestPropertySource(properties = {
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
        QuerydslShowListQueryPortTest.QuerydslTestConfig.class,
        QuerydslShowListQueryPortTest.TestConfig.class,
        QuerydslShowListQueryPortTest.AuditingTestConfig.class,
        QuerydslShowListQueryPort.class,
        QuerydslShowPredicates.class,
        SaleDisplayStatusPredicateFactory.class,
        QuerydslShowConditionBuilder.class,
        QuerydslShowSortResolver.class,
        QuerydslShowCursorConditionBuilder.class,
        ShowCardImagePathConverter.class,
        VenueLookupService.class,
        QuerydslVenueSummaryQueryPort.class,
        VenueSeatLookupService.class,
        QuerydslVenueSeatQueryPort.class
})
@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowListQueryPortTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ShowListQueryPort showListQueryPort;

    private Venue seoulVenue;
    private Venue busanVenue;

    @BeforeEach
    void setUp() throws Exception {
        seoulVenue = persistVenue("Seoul Hall", Region.SEOUL);
        busanVenue = persistVenue("Busan Hall", Region.GYEONGSANG);

        persistShow("Seoul Popular", 300L, LocalDate.now().plusDays(5), seoulVenue, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10));
        persistShow("Seoul Normal", 120L, LocalDate.now().plusDays(10), seoulVenue, LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(8));
        persistShow("Busan Hit", 999L, LocalDate.now().plusDays(3), busanVenue, LocalDateTime.now().minusDays(3), LocalDateTime.now().plusDays(7));
        persistShow("Closed Show", 50L, LocalDate.now().minusDays(1), seoulVenue, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(2));

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 지역으로_필터링하고_인기순으로_공연을_조회한다() {
        ShowParam param = new ShowParam(null, null, Region.SEOUL, null);

        CursorPage<ShowListItemRow, ShowCursor> result = showListQueryPort.findAllBySearch(param, 10, ShowSort.POPULAR);
        List<ShowListItemRow> slice = result.items();

        assertThat(slice).extracting(ShowListItemRow::title)
                .containsExactly("Seoul Popular", "Seoul Normal", "Closed Show");
        assertThat(slice).extracting(ShowListItemRow::viewCount)
                .containsExactly(300L, 120L, 50L);
        assertThat(result.nextPosition()).isNull();
    }

    @Test
    void 커서를_전달하면_다음_페이지를_조회한다() {
        ShowParam firstPageParam = new ShowParam(null, null, Region.SEOUL, null);
        CursorPage<ShowListItemRow, ShowCursor> firstPage = showListQueryPort.findAllBySearch(firstPageParam, 1, ShowSort.POPULAR);

        ShowParam secondPageParam = new ShowParam(null, null, Region.SEOUL, firstPage.nextPosition());
        CursorPage<ShowListItemRow, ShowCursor> secondPage = showListQueryPort.findAllBySearch(secondPageParam, 1, ShowSort.POPULAR);

        assertThat(firstPage.items()).extracting(ShowListItemRow::title)
                .containsExactly("Seoul Popular");
        assertThat(firstPage.nextPosition()).isNotNull();
        assertThat(secondPage.items()).extracting(ShowListItemRow::title)
                .containsExactly("Seoul Normal");
    }

    @Test
    void 검색_조건에_맞는_공연만_집계한다() {
        ShowSearchCriteria request = new ShowSearchCriteria(
                "Seoul",
                null,
                SaleDisplayStatus.ON_SALE,
                null,
                null,
                Region.SEOUL,
                null
        );

        long count = showListQueryPort.countSearchShows(request);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void 검색_api는_판매중인_서울_공연만_조회한다() {
        ShowSearchCriteria request = new ShowSearchCriteria(
                "Seoul",
                null,
                SaleDisplayStatus.ON_SALE,
                null,
                null,
                Region.SEOUL,
                null
        );

        CursorPage<ShowSearchItemRow, ShowCursor> result = showListQueryPort.searchShows(request, 10, ShowSort.POPULAR);

        assertThat(result.items()).extracting(ShowSearchItemRow::title)
                .containsExactly("Seoul Popular", "Seoul Normal");
    }

    @Test
    void 조건에_맞는_공연이_없으면_빈_슬라이스를_반환한다() {
        ShowParam param = new ShowParam(null, null, Region.JEOLLA, null);

        CursorPage<ShowListItemRow, ShowCursor> result = showListQueryPort.findAllBySearch(param, 10, ShowSort.POPULAR);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
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
                2.0
        );
        entityManager.persist(venue);
        return venue;
    }

    private Show persistShow(
            final String title,
            final long viewCount,
            final LocalDate startDate,
            final Venue venue,
            final LocalDateTime saleStartDate,
            final LocalDateTime saleEndDate
    ) {
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
                120
        );
        entityManager.persist(show);
        return show;
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
    @Import({TestConfig.class, AuditingTestConfig.class})
    static class TestApplication {
    }
}
