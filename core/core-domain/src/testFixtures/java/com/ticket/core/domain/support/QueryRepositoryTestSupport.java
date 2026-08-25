package com.ticket.core.domain.support;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.model.Category;
import com.ticket.core.domain.show.model.Genre;
import com.ticket.core.domain.show.mapping.ShowGenre;
import com.ticket.core.domain.show.mapping.ShowGrade;
import com.ticket.core.domain.show.mapping.ShowSeat;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.performer.Performer;
import com.ticket.core.domain.show.query.BookingStatusWindowPolicy;
import com.ticket.core.domain.show.query.ShowConditionFactory;
import com.ticket.core.domain.show.query.ShowCursorPolicy;
import com.ticket.core.domain.show.query.ShowQueryHelper;
import com.ticket.core.domain.show.query.ShowSortSupport;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.core.domain.showlike.model.ShowLike;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.member.model.Role;
import com.ticket.core.support.cursor.CursorCodec;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = QueryRepositoryTestSupport.TestApplication.class)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:query-repository-test;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
        QueryRepositoryTestSupport.QuerydslTestConfig.class,
        QueryRepositoryTestSupport.TestConfig.class,
        QueryRepositoryTestSupport.AuditingTestConfig.class,
        ShowQueryHelper.class,
        BookingStatusWindowPolicy.class,
        ShowConditionFactory.class,
        ShowSortSupport.class,
        ShowCursorPolicy.class
})
@SuppressWarnings("NonAsciiCharacters")
public abstract class QueryRepositoryTestSupport {

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected Clock clock;

    protected Venue persistVenue(final String name, final Region region) throws Exception {
        Venue venue = Venue.create(
                name,
                name + " 주소",
                region,
                "상세",
                "12345",
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                "02-0000-0000",
                "https://example.com/venue.png",
                1000,
                800,
                12.0,
                2.0,
                2.0
        );
        entityManager.persist(venue);
        return venue;
    }

    protected Performer persistPerformer(final String name) throws Exception {
        Performer performer = Performer.create(name, "https://example.com/performer.png");
        entityManager.persist(performer);
        return performer;
    }

    protected Category persistCategory(final String code, final String name) throws Exception {
        Category category = Category.of(code, name);
        entityManager.persist(category);
        return category;
    }

    protected Genre persistGenre(final String code, final String name, final Category category) {
        Genre genre = new Genre(code, name, category);
        entityManager.persist(genre);
        return genre;
    }

    protected Show persistShow(
            final String title,
            final Venue venue,
            final Performer performer,
            final long viewCount,
            final LocalDateTime saleStartDate,
            final LocalDateTime saleEndDate
    ) {
        Show show = new Show(
                title,
                title + " 부제",
                title + " 소개",
                LocalDate.now(clock).plusDays(1),
                LocalDate.now(clock).plusDays(30),
                viewCount,
                SaleType.GENERAL,
                saleStartDate,
                saleEndDate,
                "https://example.com/show.png",
                venue,
                performer,
                120
        );
        entityManager.persist(show);
        return show;
    }

    protected ShowGenre persistShowGenre(final Show show, final Genre genre) {
        ShowGenre showGenre = new ShowGenre(show, genre);
        entityManager.persist(showGenre);
        return showGenre;
    }

    protected ShowGrade persistShowGrade(final Show show, final String gradeCode, final String gradeName, final BigDecimal price, final int sortOrder)
            throws Exception {
        ShowGrade showGrade = ShowGrade.link(show, gradeCode, gradeName, price, sortOrder);
        entityManager.persist(showGrade);
        return showGrade;
    }

    protected Seat persistSeat(final String section, final String rowNo, final String seatNo, final int floor) {
        Seat seat = new Seat(section, rowNo, seatNo, floor, 10.0, 20.0);
        entityManager.persist(seat);
        return seat;
    }

    protected ShowSeat persistShowSeat(final Show show, final Seat seat, final ShowGrade showGrade) throws Exception {
        ShowSeat showSeat = ShowSeat.link(show, seat, showGrade);
        entityManager.persist(showSeat);
        return showSeat;
    }

    protected Performance persistPerformance(final Show show, final long performanceNo, final LocalDateTime startTime) {
        Performance performance = new Performance(
                show,
                performanceNo,
                startTime,
                startTime.plusHours(2),
                startTime.minusDays(10),
                startTime.plusDays(1),
                4,
                300
        );
        entityManager.persist(performance);
        return performance;
    }

    protected PerformanceSeat persistPerformanceSeat(
            final Performance performance,
            final Seat seat,
            final PerformanceSeatState state,
            final BigDecimal price
    ) {
        PerformanceSeat performanceSeat = new PerformanceSeat(performance, seat, state, price);
        entityManager.persist(performanceSeat);
        return performanceSeat;
    }

    protected Member persistMember(final String email, final String name) {
        Member member = Member.createSocialMember(Email.create(email), name, Role.MEMBER);
        entityManager.persist(member);
        return member;
    }

    protected ShowLike persistShowLike(final Member member, final Show show) {
        ShowLike showLike = new ShowLike(member, show);
        entityManager.persist(showLike);
        return showLike;
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
    static class TestConfig {
        @Bean
        CursorCodec cursorCodec() {
            return new CursorCodec(JsonMapper.builder().build());
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
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
    @EntityScan(basePackages = "com.ticket.core.domain")
    @Import({TestConfig.class, AuditingTestConfig.class})
    static class TestApplication {
    }
}
