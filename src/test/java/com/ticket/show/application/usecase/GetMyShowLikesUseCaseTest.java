package com.ticket.show.application.usecase;

import com.ticket.show.application.port.ShowSummaryBatchQueryPort;

import com.ticket.show.application.port.ShowSummaryBatchQueryPort;
import com.ticket.show.application.ShowSummaryRow;
import com.ticket.show.application.ShowLikeSummaryView;
import com.ticket.error.InvalidRequestException;
import com.ticket.like.LikeEntry;
import com.ticket.like.LikeQuery;
import com.ticket.like.LikeType;
import com.ticket.member.MemberLookup;
import com.ticket.shared.CursorPage;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetMyShowLikesUseCaseTest {

    @Mock
    private MemberLookup memberLookup;

    @Mock
    private LikeQuery likeQuery;

    @Mock
    private ShowSummaryBatchQueryPort showSummaryBatchQueryPort;

    @Mock
    private VenueLookup venueLookup;

    @InjectMocks
    private GetMyShowLikesUseCase useCase;

    @Test
    void 찜한_공연_목록을_show_표시값과_조합해_반환한다() {
        LocalDateTime likedAt = LocalDateTime.now();
        LikeEntry entry = new LikeEntry(9L, 2L, likedAt);
        when(likeQuery.findLiked(LikeType.SHOW, 1L, 10L, 20))
                .thenReturn(new CursorPage<>(List.of(entry), true, 9L));

        ShowSummaryRow summary = new ShowSummaryRow(
                2L, "공연", "image", LocalDate.now(), LocalDate.now().plusDays(1), 7L
        );
        when(showSummaryBatchQueryPort.findSummaries(Set.of(2L))).thenReturn(Map.of(2L, summary));
        when(venueLookup.getSummaries(Set.of(7L))).thenReturn(Map.of(7L, new VenueSummary(
                7L, "장소", "주소", null, null, null, null, null,
                new VenueSummary.SeatMapLayout(0, 0, 0.0)
        )));

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, 10L, 20));

        assertThat(output.items()).containsExactly(
                new ShowLikeSummaryView(2L, "공연", "image", summary.startDate(), summary.endDate(), "장소", likedAt)
        );
        assertThat(output.hasNext()).isTrue();
        assertThat(output.nextPosition()).isEqualTo(9L);
        verify(memberLookup).requireActive(1L);
        verify(likeQuery).findLiked(LikeType.SHOW, 1L, 10L, 20);
    }

    @Test
    void show_표시값을_찾지_못한_항목은_건너뛴다() {
        LikeEntry entry = new LikeEntry(9L, 2L, LocalDateTime.now());
        when(likeQuery.findLiked(LikeType.SHOW, 1L, null, 20))
                .thenReturn(new CursorPage<>(List.of(entry), false, null));
        when(showSummaryBatchQueryPort.findSummaries(Set.of(2L))).thenReturn(Map.of());

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, null, 20));

        assertThat(output.items()).isEmpty();
        assertThat(output.hasNext()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_size가_유효하지_않으면_Input_생성에서_예외를_던진다(
            final Long memberId,
            final int size
    ) {
        assertThatThrownBy(() -> new GetMyShowLikesUseCase.Input(memberId, null, size))
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

    @Test
    void 커서_위치가_없으면_첫_페이지를_조회한다() {
        when(likeQuery.findLiked(LikeType.SHOW, 1L, null, 20))
                .thenReturn(CursorPage.empty());

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, null, 20));

        assertThat(output.items()).isEmpty();
        assertThat(output.hasNext()).isFalse();
        assertThat(output.nextPosition()).isNull();
        verify(likeQuery).findLiked(LikeType.SHOW, 1L, null, 20);
    }

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(
                Arguments.of(null, 20),
                Arguments.of(0L, 20),
                Arguments.of(1L, 0),
                Arguments.of(1L, 101)
        );
    }
}
