package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeType;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.domain.GradeRepository;
import com.ticket.show.domain.Performer;
import com.ticket.show.domain.PerformerRepository;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.show.usecase.GetShowDetailUseCase.PriceSummary;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowDetailUseCaseTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 3, 15, 12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Mock
    private ShowQuerydslRepository showQuerydslRepository;

    @Mock
    private ShowRepository showRepository;

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private PerformerRepository performerRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private LikeQueryApi likeQuery;

    @Mock
    private VenueLookupApi venueLookup;

    private GetShowDetailUseCase useCase() {
        return new GetShowDetailUseCase(
                showQuerydslRepository,
                showRepository,
                gradeRepository,
                performerRepository,
                performanceRepository,
                likeQuery,
                venueLookup,
                new ShowCardImagePathConverter(),
                FIXED_CLOCK);
    }

    private static Show show(final @Nullable Long venueId, final @Nullable Long performerId) {
        final Show show = new Show(
                "공연",
                "부제",
                "info",
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 20),
                100L,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 3, 20, 23, 59),
                "/api/images/shows/1.png",
                venueId,
                performerId,
                120);
        ReflectionTestUtils.setField(show, "id", 1L);
        return show;
    }

    private void stubEmptyFragments() {
        when(showRepository.findGenreNames(1L)).thenReturn(List.of());
        when(performanceRepository.findRepresentativePerformanceGrades(1L)).thenReturn(List.of());
        when(performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(1L))
                .thenReturn(List.of());
    }

    @Test
    void show_엔티티에서_응답을_만들고_찜_개수와_venue_표시값을_조합한다() {
        when(showRepository.findById(1L)).thenReturn(Optional.of(show(5L, null)));
        when(showRepository.findGenreNames(1L)).thenReturn(List.of("장르"));
        when(performanceRepository.findRepresentativePerformanceGrades(1L)).thenReturn(List.of());
        when(showQuerydslRepository.findPriceSummary(1L))
                .thenReturn(new PriceSummary(BigDecimal.valueOf(100000), BigDecimal.valueOf(200000)));
        when(performanceRepository.findAllByShowIdOrderByStartTimeAscPerformanceNoAsc(1L))
                .thenReturn(List.of());
        when(likeQuery.countByTarget(LikeType.SHOW, 1L)).thenReturn(10L);
        when(venueLookup.getVenueSnapshot(5L))
                .thenReturn(new VenueSnapshot(
                        5L,
                        "예술의전당",
                        "주소",
                        new VenueSnapshot.RegionView("SEOUL", "서울"),
                        null,
                        null,
                        null,
                        null,
                        new VenueSnapshot.SeatMapLayout(0, 0, 0.0)));

        GetShowDetailUseCase.Output output = useCase().execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.id()).isEqualTo(1L);
        assertThat(output.title()).isEqualTo("공연");
        assertThat(output.subTitle()).isEqualTo("부제");
        assertThat(output.info()).isEqualTo("info");
        assertThat(output.runningMinutes()).isEqualTo(120);
        assertThat(output.viewCount()).isEqualTo(100L);
        assertThat(output.displaySaleType()).isEqualTo(SaleType.GENERAL);
        assertThat(output.genreNames()).containsExactly("장르");
        assertThat(output.priceSummary().minPrice()).isEqualByComparingTo("100000");
        assertThat(output.likeCount()).isEqualTo(10L);
        assertThat(output.venue().name()).isEqualTo("예술의전당");
        assertThat(output.performer()).isNull();
        verify(likeQuery).countByTarget(LikeType.SHOW, 1L);
        verify(venueLookup).getVenueSnapshot(5L);
    }

    @Test
    void 출연자_ID로_조회한_정보를_응답에_담는다() {
        stubShowWithPerformer(7L);
        Performer performer = Performer.create("아이유", "/performers/7.png");
        ReflectionTestUtils.setField(performer, "id", 7L);
        when(performerRepository.findById(7L)).thenReturn(Optional.of(performer));

        var output = useCase().execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.performer())
                .isEqualTo(new GetShowDetailUseCase.PerformerInfo(7L, "아이유", "/performers/7.png"));
    }

    @Test
    void 출연자_ID가_없으면_조회하지_않고_null을_반환한다() {
        stubShowWithPerformer(null);

        var output = useCase().execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.performer()).isNull();
        verifyNoInteractions(performerRepository);
    }

    @Test
    void 참조한_출연자가_없으면_null을_반환한다() {
        stubShowWithPerformer(7L);
        when(performerRepository.findById(7L)).thenReturn(Optional.empty());

        var output = useCase().execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.performer()).isNull();
    }

    private void stubShowWithPerformer(final @Nullable Long performerId) {
        when(showRepository.findById(1L)).thenReturn(Optional.of(show(5L, performerId)));
        stubEmptyFragments();
        when(venueLookup.getVenueSnapshot(5L))
                .thenReturn(new VenueSnapshot(
                        5L, "공연장", null, null, null, null, null, null, new VenueSnapshot.SeatMapLayout(0, 0, 0.0)));
    }

    /** 예매 상태는 저장된 값이 아니라 주입된 Clock 기준으로 계산한다. */
    @Test
    void 예매_상태는_Clock_기준으로_계산한다() {
        stubShowWithPerformer(null);

        GetShowDetailUseCase.Output output = useCase().execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.saleDisplayStatus()).isEqualTo(SaleDisplayStatus.ON_SALE);
    }

    /** 카드 이미지 경로 변환은 조회가 아니라 응답 조립 단계의 일이다. */
    @Test
    void 이미지_경로를_카드_이미지로_바꾼다() {
        stubShowWithPerformer(null);

        GetShowDetailUseCase.Output output = useCase().execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.image()).isEqualTo("/api/images/shows/card/1.jpg");
    }

    @Test
    void 공연_상세가_없으면_not_found_data_예외를_던진다() {
        when(showRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(new GetShowDetailUseCase.Input(1L)))
                .isInstanceOf(NotFoundException.class);
    }

    /** showId 계약은 Input 생성자 한곳에서만 판정한다. execute가 같은 검사를 반복하지 않는다. */
    @Test
    void showId가_유효하지_않으면_Input_생성에서_예외를_던진다() {
        assertThatThrownBy(() -> new GetShowDetailUseCase.Input(null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new GetShowDetailUseCase.Input(0L)).isInstanceOf(InvalidRequestException.class);
    }

    /** Input을 아예 넘기지 않은 것은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다. */
    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        assertThatThrownBy(() -> useCase().execute(null)).isInstanceOf(NullPointerException.class);
    }
}
