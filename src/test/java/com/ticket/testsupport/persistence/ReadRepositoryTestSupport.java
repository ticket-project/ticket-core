package com.ticket.testsupport.persistence;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatState;
import com.ticket.show.domain.Category;
import com.ticket.show.domain.Genre;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.Performer;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowGenre;
import com.ticket.venue.domain.Region;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.Venue;
import com.ticket.venue.persistence.SeatRepositoryAdapter;
import com.ticket.venue.persistence.VenueRepositoryAdapter;
import com.ticket.venue.usecase.SeatLookupService;
import com.ticket.venue.usecase.VenueLookupService;

/**
 * 실제 JPA·Querydsl 조회를 H2에 붙여 검증하는 테스트의 베이스다. 테스트마다 트랜잭션을 롤백한다.
 *
 * <p>venue 공개 계약({@code VenueLookupApi}/{@code VenueSeatLookupApi})의 구현을 빈으로 올린다 — 이 베이스를 쓰는 테스트가 그 계약을 주입받으면 없을 때 컨텍스트
 * 기동부터 실패한다. 두 계약은 {@link VenueRepositoryAdapter} 하나가 함께 구현한다.
 *
 * <p>show의 정렬·커서·판매 상태 조건 helper는 더 이상 별도 빈이 아니다 — {@code ShowQuerydslRepository}가 private 메서드로 갖는다.
 */
@Transactional
@Import({VenueRepositoryAdapter.class, SeatRepositoryAdapter.class, VenueLookupService.class, SeatLookupService.class})
@SuppressWarnings("NonAsciiCharacters")
public abstract class ReadRepositoryTestSupport extends JpaSliceTestSupport {
    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected Clock clock;

    protected Venue persistVenue(final String name, final Region region) throws Exception {
        Venue venue = Venue.create(
                name,
                name + " 주소",
                region,
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                "02-0000-0000",
                "https://example.com/venue.png",
                1000,
                800,
                12.0);
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
            final LocalDateTime saleEndDate) {
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
                120);
        entityManager.persist(show);
        return show;
    }

    protected ShowGenre persistShowGenre(final Show show, final Genre genre) {
        ShowGenre showGenre = new ShowGenre(show.getId(), genre.getId());
        entityManager.persist(showGenre);
        return showGenre;
    }

    protected Seat persistSeat(
            final Venue venue, final String section, final String rowNo, final String seatNo, final int floor) {
        Seat seat = new Seat(venue.getId(), section, rowNo, seatNo, floor, 10.0, 20.0);
        entityManager.persist(seat);
        return seat;
    }

    protected Performance persistPerformance(final Show show, final long performanceNo, final LocalDateTime startTime) {
        Performance performance = new Performance(show.getId(), performanceNo, startTime, startTime.plusHours(2));
        entityManager.persist(performance);
        return performance;
    }

    protected Grade persistGrade(final String code, final String name) {
        Grade grade = Grade.of(code, name);
        entityManager.persist(grade);
        return grade;
    }

    protected PerformanceGrade persistPerformanceGrade(
            final Performance performance, final Grade grade, final BigDecimal price, final int sortOrder) {
        PerformanceGrade performanceGrade = PerformanceGrade.assign(performance, grade.getId(), price, sortOrder);
        entityManager.persist(performanceGrade);
        return performanceGrade;
    }

    protected PerformanceSeat persistPerformanceSeat(
            final Performance performance, final Seat seat, final PerformanceSeatState state, final BigDecimal price) {
        PerformanceSeat performanceSeat = new PerformanceSeat(performance.getId(), seat.getId(), 1L, state, price);
        entityManager.persist(performanceSeat);
        return performanceSeat;
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
