package com.ticket.core.app.showlike.query;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.app.showlike.query.ShowLikeQueryRepository;
import com.ticket.core.app.support.cursor.CursorSlice;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetMyShowLikesUseCaseTest {

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private ShowLikeQueryRepository showLikeQueryRepository;

    @InjectMocks
    private GetMyShowLikesUseCase useCase;

    @Test
    void 찜한_공연_목록을_커서와_함께_조회한다() {
        Member member = mock(Member.class);
        GetMyShowLikesUseCase.ShowLikeSummary summary = new GetMyShowLikesUseCase.ShowLikeSummary(
                2L,
                "공연",
                "image",
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "장소",
                LocalDateTime.now()
        );
        when(member.getId()).thenReturn(1L);
        when(memberFinder.findActiveMemberById(1L)).thenReturn(member);
        when(showLikeQueryRepository.findMyLikedShows(1L, 10L, 20))
                .thenReturn(new CursorSlice<>(new SliceImpl<>(List.of(summary)), "9"));

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, "10", 20));

        assertThat(output.shows().getContent()).containsExactly(summary);
        assertThat(output.nextCursor()).isEqualTo("9");
        verify(showLikeQueryRepository).findMyLikedShows(1L, 10L, 20);
    }

    @Test
    void cursor가_숫자가_아니면_예외를_던진다() {
        assertThatThrownBy(() -> useCase.execute(new GetMyShowLikesUseCase.Input(1L, "abc", 20)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void memberId_또는_size가_유효하지_않으면_예외를_던진다(final GetMyShowLikesUseCase.Input input) {
        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void cursor가_비어있으면_첫_페이지를_조회한다() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberFinder.findActiveMemberById(1L)).thenReturn(member);
        when(showLikeQueryRepository.findMyLikedShows(1L, null, 20))
                .thenReturn(new CursorSlice<>(new SliceImpl<>(List.of()), null));

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, " ", 20));

        assertThat(output.shows().getContent()).isEmpty();
        assertThat(output.nextCursor()).isNull();
        verify(showLikeQueryRepository).findMyLikedShows(1L, null, 20);
    }

    private static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(new GetMyShowLikesUseCase.Input(null, null, 20)),
                Arguments.of(new GetMyShowLikesUseCase.Input(1L, null, 0)),
                Arguments.of(new GetMyShowLikesUseCase.Input(1L, null, 101))
        );
    }
}
