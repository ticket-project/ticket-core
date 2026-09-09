package com.ticket.show.application.usecase;

import com.ticket.show.application.PriceSummary;

import com.ticket.show.application.port.ShowDetailQueryPort;

import com.ticket.show.domain.SaleType;
import com.ticket.show.domain.SaleDisplayStatus;
import com.ticket.show.application.ShowDetailView;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.like.LikeQuery;
import com.ticket.like.LikeType;
import com.ticket.venue.Region;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowDetailUseCaseTest {

    @Mock
    private ShowDetailQueryPort showDetailQueryPort;

    @Mock
    private LikeQuery likeQuery;

    @Mock
    private VenueLookup venueLookup;

    @InjectMocks
    private GetShowDetailUseCase useCase;

    @Test
    void 공연_상세를_그대로_반환하고_찜_개수와_venue_표시값을_조합한다() {
        ShowDetailView detail = new ShowDetailView(
                1L,
                "공연",
                "부제",
                "info",
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                120,
                100L,
                SaleDisplayStatus.ON_SALE,
                SaleType.GENERAL,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                "image",
                5L,
                null,
                List.of("장르"),
                new PriceSummary(java.math.BigDecimal.valueOf(100000), java.math.BigDecimal.valueOf(200000)),
                List.of()
        );
        when(showDetailQueryPort.findShowDetail(1L)).thenReturn(Optional.of(detail));
        when(likeQuery.countByTarget(LikeType.SHOW, 1L)).thenReturn(10L);
        when(venueLookup.findSummary(5L)).thenReturn(Optional.of(new VenueSummary(
                5L, "예술의전당", "주소", Region.SEOUL, null, null, null, null,
                new VenueSummary.SeatMapLayout(0, 0, 0.0)
        )));

        GetShowDetailUseCase.Output output = useCase.execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.id()).isEqualTo(detail.id());
        assertThat(output.title()).isEqualTo(detail.title());
        assertThat(output.saleDisplayStatus()).isEqualTo(detail.saleDisplayStatus());
        assertThat(output.genreNames()).isEqualTo(detail.genreNames());
        assertThat(output.likeCount()).isEqualTo(10L);
        assertThat(output.venue().name()).isEqualTo("예술의전당");
        verify(showDetailQueryPort).findShowDetail(1L);
        verify(likeQuery).countByTarget(LikeType.SHOW, 1L);
        verify(venueLookup).findSummary(5L);
    }

    @Test
    void venueId가_없으면_venue_조회_없이_null을_반환한다() {
        ShowDetailView detail = new ShowDetailView(
                1L, "공연", "부제", "info", LocalDate.now(), LocalDate.now().plusDays(1),
                120, 100L, SaleDisplayStatus.ON_SALE, SaleType.GENERAL,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), "image",
                null, null, List.of(), null, List.of()
        );
        when(showDetailQueryPort.findShowDetail(1L)).thenReturn(Optional.of(detail));
        when(likeQuery.countByTarget(LikeType.SHOW, 1L)).thenReturn(0L);

        GetShowDetailUseCase.Output output = useCase.execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output.venue()).isNull();
    }

    @Test
    void 공연_상세가_없으면_not_found_data_예외를_던진다() {
        when(showDetailQueryPort.findShowDetail(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetShowDetailUseCase.Input(1L)))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * showId 계약은 Input 생성자 한곳에서만 판정한다. execute가 같은 검사를 반복하지 않는다.
     */
    @Test
    void showId가_유효하지_않으면_Input_생성에서_예외를_던진다() {
        assertThatThrownBy(() -> new GetShowDetailUseCase.Input(null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new GetShowDetailUseCase.Input(0L))
                .isInstanceOf(InvalidRequestException.class);
    }

    /**
     * Input을 아예 넘기지 않은 것은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다.
     */
    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(NullPointerException.class);
    }
}
