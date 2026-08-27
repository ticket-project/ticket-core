package com.ticket.core.infra.show.query;

import com.ticket.core.app.show.query.ShowListReadRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.show.BookingStatus;
import com.ticket.core.domain.show.image.ShowCardImagePathConverter;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.app.show.query.model.ShowListItemView;
import com.ticket.core.app.show.query.model.ShowParam;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import com.ticket.core.app.show.query.model.ShowSearchItemView;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.core.app.support.cursor.CursorPage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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

@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = QuerydslShowListReadRepositoryTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:show-query-test;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV4"
})
@Transactional
@Import({
        QuerydslShowListReadRepositoryTest.QuerydslTestConfig.class,
        QuerydslShowListReadRepositoryTest.TestConfig.class,
        QuerydslShowListReadRepositoryTest.AuditingTestConfig.class,
        QuerydslShowListReadRepository.class,
        ShowQueryHelper.class,
        BookingStatusWindowPolicy.class,
        ShowConditionFactory.class,
        ShowSortSupport.class,
        ShowCursorPolicy.class,
        ShowCardImagePathConverter.class
})
@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowListReadRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ShowListReadRepository showListReadRepository;

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

        CursorPage<ShowListItemView, ShowCursor> result = showListReadRepository.findAllBySearch(param, 10, "popular");
        List<ShowListItemView> slice = result.items();

        assertThat(slice).extracting(ShowListItemView::title)
                .containsExactly("Seoul Popular", "Seoul Normal", "Closed Show");
        assertThat(slice).extracting(ShowListItemView::viewCount)
                .containsExactly(300L, 120L, 50L);
        assertThat(result.nextPosition()).isNull();
    }

    @Test
    void 커서를_전달하면_다음_페이지를_조회한다() {
        ShowParam firstPageParam = new ShowParam(null, null, Region.SEOUL, null);
        CursorPage<ShowListItemView, ShowCursor> firstPage = showListReadRepository.findAllBySearch(firstPageParam, 1, "popular");

        ShowParam secondPageParam = new ShowParam(null, null, Region.SEOUL, firstPage.nextPosition());
        CursorPage<ShowListItemView, ShowCursor> secondPage = showListReadRepository.findAllBySearch(secondPageParam, 1, "popular");

        assertThat(firstPage.items()).extracting(ShowListItemView::title)
                .containsExactly("Seoul Popular");
        assertThat(firstPage.nextPosition()).isNotNull();
        assertThat(secondPage.items()).extracting(ShowListItemView::title)
                .containsExactly("Seoul Normal");
    }

    @Test
    void 검색_조건에_맞는_공연만_집계한다() {
        ShowSearchCriteria request = new ShowSearchCriteria(
                "Seoul",
                null,
                BookingStatus.ON_SALE,
                null,
                null,
                Region.SEOUL,
                null
        );

        long count = showListReadRepository.countSearchShows(request);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void 검색_api는_판매중인_서울_공연만_조회한다() {
        ShowSearchCriteria request = new ShowSearchCriteria(
                "Seoul",
                null,
                BookingStatus.ON_SALE,
                null,
                null,
                Region.SEOUL,
                null
        );

        CursorPage<ShowSearchItemView, ShowCursor> result = showListReadRepository.searchShows(request, 10, "popular");

        assertThat(result.items()).extracting(ShowSearchItemView::title)
                .containsExactly("Seoul Popular", "Seoul Normal");
    }

    @Test
    void 조건에_맞는_공연이_없으면_빈_슬라이스를_반환한다() {
        ShowParam param = new ShowParam(null, null, Region.JEOLLA, null);

        CursorPage<ShowListItemView, ShowCursor> result = showListReadRepository.findAllBySearch(param, 10, "popular");

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
                venue,
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = {"com.ticket.core.domain", "com.ticket.core.infra"})
    @Import({TestConfig.class, AuditingTestConfig.class})
    static class TestApplication {
    }
}
