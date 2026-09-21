package com.ticket.show.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.ticket.show.domain.Category;
import com.ticket.show.domain.Genre;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.GradeRepository;
import com.ticket.show.domain.Performer;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.domain.Venue;

/** 공연 상세 응답을 만들 때 쓰는 조회 조각들을 고정한다 — Querydsl 조각과 계약 조각이 함께 한 응답을 이룬다. */
@Import({
    ShowQuerydslRepository.class,
    ShowRepositoryAdapter.class,
    GradeRepositoryAdapter.class,
    PerformanceRepositoryAdapter.class,
    ShowCardImagePathConverter.class
})
@SuppressWarnings("NonAsciiCharacters")
class ShowQuerydslRepositoryDetailTest extends InfraReadRepositoryTestSupport {
    @Autowired private ShowQuerydslRepository showQuerydslRepository;
    @Autowired private ShowRepository showRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private PerformanceRepository performanceRepository;
    private Long showId;
    private Long venueId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("예술의전당", Region.SEOUL);
        venueId = venue.getId();
        Performer performer = persistPerformer("홍길동");
        Category category = persistCategory("CONCERT", "콘서트");
        Genre genre = persistGenre("KPOP", "케이팝", category);
        Show show =
                persistShow(
                        "단독 공연",
                        venue,
                        performer,
                        321L,
                        LocalDateTime.of(2026, 3, 10, 0, 0),
                        LocalDateTime.of(2026, 3, 20, 23, 59));
        showId = show.getId();
        entityManager
                .createNativeQuery("update shows set image = :image where id = :id")
                .setParameter("image", "/api/images/shows/" + showId + ".png")
                .setParameter("id", showId)
                .executeUpdate();
        persistShowGenre(show, genre);
        Performance firstPerformance =
                persistPerformance(show, 1L, LocalDate.of(2026, 3, 16).atTime(14, 0));
        Performance secondPerformance =
                persistPerformance(show, 2L, LocalDate.of(2026, 3, 16).atTime(19, 0));
        // ADR 0005: 가격은 회차(Performance) 단위로만 존재한다. 두 회차에 서로 다른 가격 범위를 둬
        // show 상세의 priceSummary가 회차 전체의 min/max를 파생하는지 확인한다.
        Grade vip = persistGrade("VIP", "VIP석");
        Grade r = persistGrade("R", "R석");
        persistPerformanceGrade(firstPerformance, vip, BigDecimal.valueOf(150000), 1);
        persistPerformanceGrade(firstPerformance, r, BigDecimal.valueOf(100000), 2);
        persistPerformanceGrade(secondPerformance, vip, BigDecimal.valueOf(180000), 1);
        flushAndClear();
    }

    @Test
    void 공연_상세에_쓰는_show_엔티티를_조회한다() {
        Optional<Show> result = showRepository.findById(showId);

        assertThat(result).isPresent();
        Show show = result.orElseThrow();
        assertThat(show.getTitle()).isEqualTo("단독 공연");
        assertThat(show.getVenueId()).isEqualTo(venueId);
        assertThat(show.getImage()).isEqualTo("/api/images/shows/" + showId + ".png");
        assertThat(show.saleDisplayStatusAt(LocalDateTime.of(2026, 3, 15, 12, 0)))
                .isEqualTo(SaleDisplayStatus.ON_SALE);
        assertThat(
                        showQuerydslRepository
                                .findPerformer(show.getPerformerId())
                                .orElseThrow()
                                .getName())
                .isEqualTo("홍길동");
    }

    @Test
    void 장르_이름을_조회한다() {
        assertThat(showRepository.findGenreNames(showId)).contains("케이팝");
    }

    /** ADR 0005: show-level 가격표는 없다 — 회차 전체의 min/max를 파생한다. */
    @Test
    void 가격_요약은_회차_전체의_최소_최대다() {
        assertThat(showQuerydslRepository.findPriceSummary(showId).minPrice())
                .isEqualByComparingTo("100000");
        assertThat(showQuerydslRepository.findPriceSummary(showId).maxPrice())
                .isEqualByComparingTo("180000");
    }

    /** 대표 가격표는 가장 이른 회차의 등급을 표시 순서대로 준다. 등급 이름은 use case가 따로 조합한다. */
    @Test
    void 대표_회차의_등급을_표시_순서대로_조회한다() {
        var performanceGrades = showQuerydslRepository.findRepresentativePerformanceGrades(showId);
        var gradesById =
                gradeRepository.findGradeNames(
                        performanceGrades.stream()
                                .map(
                                        com.ticket.show.domain.performance.PerformanceGrade
                                                ::getGradeId)
                                .toList());

        assertThat(performanceGrades)
                .extracting(
                        performanceGrade ->
                                gradesById.get(performanceGrade.getGradeId()).getName()
                                        + ":"
                                        + performanceGrade.getPrice())
                .containsExactly("VIP석:150000", "R석:100000");
    }

    @Test
    void 회차를_시작_시각과_회차_번호_순서로_조회한다() {
        var performances =
                performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(showId);

        assertThat(performances).hasSize(2);
        assertThat(performances.getFirst().getPerformanceNo()).isEqualTo(1L);
        assertThat(performances.get(1).getPerformanceNo()).isEqualTo(2L);
    }

    @Test
    void 존재하지_않는_공연이면_empty를_반환한다() {
        assertThat(showRepository.findById(999999L)).isEmpty();
    }
}
