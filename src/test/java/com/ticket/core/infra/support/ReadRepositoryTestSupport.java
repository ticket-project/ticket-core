package com.ticket.core.infra.support;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.member.domain.member.model.Member;
import com.ticket.member.domain.member.model.Email;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.grade.Grade;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeat;
import com.ticket.venue.domain.seat.Seat;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.Category;
import com.ticket.show.domain.show.Genre;
import com.ticket.show.domain.show.ShowGenre;
import com.ticket.venue.Region;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Performer;
import com.ticket.venue.domain.venue.Venue;
import com.ticket.favorite.domain.showlike.model.ShowLike;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.member.domain.member.model.Role;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = ReadRepositoryTestSupport.TestApplication.class)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:query-repository-test;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        // Spring Modulith의 ModuleObservabilityAutoConfiguration은 기본으로 켜져(matchIfMissing=true)
        // ApplicationModulesRuntime을 즉시(non-lazy) 요구하는 BeanPostProcessor를 등록한다. 이 좁은
        // 슬라이스 컨텍스트는 실제 main class(@SpringBootApplication)가 없어 그 런타임을 만들 수
        // 없으므로, 이 슬라이스에서는 tracing 관측을 꺼서 그 자동설정 자체가 활성화되지 않게 한다.
        "management.tracing.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV4,"
                // spring-modulith-actuator의 이 자동설정도 (tracing과 무관하게) ApplicationModulesRuntime을
                // ObjectProvider.getObject()로 즉시 resolve한다. management.tracing.enabled와는 별개
                // 원인이라 따로 꺼야 한다.
                + "org.springframework.modulith.actuator.autoconfigure.ApplicationModulesEndpointConfiguration,"
                // spring-modulith-runtime 자체의 이 자동설정은 항상 ApplicationModulesBootstrap을
                // 만들며 classpath에서 @SpringBootApplication 애노테이션 클래스를 찾는다. 이 좁은
                // 슬라이스는 그런 main class가 없으므로 이 자동설정 자체를 꺼서 부트스트랩 실패를 막는다.
                + "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration"
})
@Transactional
@Import({
        ReadRepositoryTestSupport.QuerydslTestConfig.class,
        ReadRepositoryTestSupport.TestConfig.class,
        ReadRepositoryTestSupport.AuditingTestConfig.class
})
/**
 * 실제 JPA·Querydsl 조회를 H2에 붙여 검증하는 테스트의 베이스다.
 *
 * <p>Spring 컨텍스트와 EntityManager가 필요하므로 core-infra의 integrationTest에 둔다.
 * 도메인 단위 테스트는 이 클래스를 쓰지 않는다.
 */
@SuppressWarnings("NonAsciiCharacters")
public abstract class ReadRepositoryTestSupport {

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
        Genre genre = new Genre(code, name, category.getId());
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
                venue == null ? null : venue.getId(),
                performer == null ? null : performer.getId(),
                120
        );
        entityManager.persist(show);
        return show;
    }

    protected ShowGenre persistShowGenre(final Show show, final Genre genre) {
        ShowGenre showGenre = new ShowGenre(show.getId(), genre.getId());
        entityManager.persist(showGenre);
        return showGenre;
    }

    protected Seat persistSeat(final Venue venue, final String section, final String rowNo, final String seatNo, final int floor) {
        Seat seat = new Seat(venue.getId(), section, rowNo, seatNo, floor, 10.0, 20.0);
        entityManager.persist(seat);
        return seat;
    }

    protected Performance persistPerformance(final Show show, final long performanceNo, final LocalDateTime startTime) {
        Performance performance = new Performance(
                show.getId(),
                performanceNo,
                startTime,
                startTime.plusHours(2)
        );
        entityManager.persist(performance);
        return performance;
    }

    protected Grade persistGrade(final String code, final String name) {
        Grade grade = Grade.of(code, name);
        entityManager.persist(grade);
        return grade;
    }

    protected PerformanceGrade persistPerformanceGrade(
            final Performance performance,
            final Grade grade,
            final BigDecimal price,
            final int sortOrder
    ) {
        PerformanceGrade performanceGrade = PerformanceGrade.assign(performance, grade.getId(), price, sortOrder);
        entityManager.persist(performanceGrade);
        return performanceGrade;
    }

    protected PerformanceSeat persistPerformanceSeat(
            final Performance performance,
            final Seat seat,
            final PerformanceSeatState state,
            final BigDecimal price
    ) {
        PerformanceSeat performanceSeat = new PerformanceSeat(performance.getId(), seat.getId(), 1L, state, price);
        entityManager.persist(performanceSeat);
        return performanceSeat;
    }

    protected Member persistMember(final String email, final String name) {
        Member member = Member.createSocialMember(Email.create(email), name, Role.MEMBER);
        entityManager.persist(member);
        return member;
    }

    protected ShowLike persistShowLike(final Member member, final Show show) {
        ShowLike showLike = new ShowLike(member.getId(), show.getId());
        entityManager.persist(showLike);
        return showLike;
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
    static class TestConfig {

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

    // @TestComponent는 Spring Boot의 TypeExcludeFilter(TestTypeExcludeFilter)가 다른
    // @SpringBootTest 컨텍스트(TicketApplication 등)의 component scan에서 이 클래스를 제외하게
    // 한다. 단일 Gradle 프로젝트로 합쳐지면서 이 테스트 전용 클래스가 실제 앱과 같은 com.ticket
    // 패키지 트리 아래 놓이게 됐고, 그 결과 @Modulith(=@SpringBootApplication)의 기본 component
    // scan이 이 클래스까지 주워 담아 clock() 같은 테스트 전용 빈이 실제 앱 빈과 충돌했다.
    // 주의: 여기 @TestConfiguration을 쓰면 안 된다 — SpringBootTestContextBootstrapper는
    // classes=... 로 명시한 설정이 전부 @TestConfiguration이면 "명시하지 않은 것"으로 보고
    // 패키지를 거슬러 올라가며 다른 @SpringBootConfiguration을 추가로 찾아 병합해버린다(이 클래스
    // 자신이 바로 그 classes=... 값이라 자기 자신도 걸린다). @TestComponent만 쓰면 TypeExcludeFilter
    // 적용은 그대로 받으면서 그 자동 탐색-병합은 피한다.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @TestComponent
    @EntityScan(basePackages = {"com.ticket.show.domain", "com.ticket.venue.domain", "com.ticket.favorite.domain", "com.ticket.member.domain", "com.ticket.booking.domain", "com.ticket.booking.infrastructure", "com.ticket.payment.domain"})
    @Import({TestConfig.class, AuditingTestConfig.class})
    static class TestApplication {
    }
}
