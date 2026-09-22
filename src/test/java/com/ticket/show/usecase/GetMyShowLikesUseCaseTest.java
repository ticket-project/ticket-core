package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeSnapshot;
import com.ticket.like.api.LikeType;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetMyShowLikesUseCaseTest {
    @Mock
    private MemberLookupApi memberLookup;

    @Mock
    private LikeQueryApi likeQuery;

    @Mock
    private ShowRepository showRepository;

    @Mock
    private VenueLookupApi venueLookup;

    @InjectMocks
    private GetMyShowLikesUseCase useCase;

    @Test
    void 찜한_공연_목록을_show_표시값과_조합해_반환한다() {
        LocalDateTime likedAt = LocalDateTime.now();
        LikeSnapshot entry = new LikeSnapshot(9L, 2L, likedAt);
        when(likeQuery.findLiked(LikeType.SHOW, 1L, 10L, 20)).thenReturn(new CursorPage<>(List.of(entry), true, 9L));

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(1);
        Show show = ShowFixture.show(2L, "공연", 7L, startDate, endDate, null, 0L, LocalDateTime.now());
        when(showRepository.findSummaries(Set.of(2L))).thenReturn(Map.of(2L, show));
        when(venueLookup.getSummaries(Set.of(7L)))
                .thenReturn(Map.of(
                        7L,
                        new VenueSnapshot(
                                7L,
                                "장소",
                                "주소",
                                null,
                                null,
                                null,
                                null,
                                null,
                                new VenueSnapshot.SeatMapLayout(0, 0, 0.0))));

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, 10L, 20));

        assertThat(output.items())
                .containsExactly(new GetMyShowLikesUseCase.Item(2L, "공연", "image", startDate, endDate, "장소", likedAt));
        assertThat(output.hasNext()).isTrue();
        assertThat(output.nextPosition()).isEqualTo(9L);
        verify(memberLookup).requireActive(1L);
        verify(likeQuery).findLiked(LikeType.SHOW, 1L, 10L, 20);
    }

    @Test
    void show_표시값을_찾지_못한_항목은_건너뛴다() {
        LikeSnapshot entry = new LikeSnapshot(9L, 2L, LocalDateTime.now());
        when(likeQuery.findLiked(LikeType.SHOW, 1L, null, 20))
                .thenReturn(new CursorPage<>(List.of(entry), false, null));
        when(showRepository.findSummaries(Set.of(2L))).thenReturn(Map.of());

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, null, 20));

        assertThat(output.items()).isEmpty();
        assertThat(output.hasNext()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_size가_유효하지_않으면_Input_생성에서_예외를_던진다(final Long memberId, final int size) {
        assertThatThrownBy(() -> new GetMyShowLikesUseCase.Input(memberId, null, size))
                .isInstanceOf(InvalidRequestException.class);
    }

    /** Input을 아예 넘기지 않은 것은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다. */
    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 커서_위치가_없으면_첫_페이지를_조회한다() {
        when(likeQuery.findLiked(LikeType.SHOW, 1L, null, 20)).thenReturn(CursorPage.empty());

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, null, 20));

        assertThat(output.items()).isEmpty();
        assertThat(output.hasNext()).isFalse();
        assertThat(output.nextPosition()).isNull();
        verify(likeQuery).findLiked(LikeType.SHOW, 1L, null, 20);
    }

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(Arguments.of(null, 20), Arguments.of(0L, 20), Arguments.of(1L, 0), Arguments.of(1L, 101));
    }
}
