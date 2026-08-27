package com.ticket.core.app.showlike.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.show.query.ShowFinder;
import com.ticket.core.domain.showlike.model.ShowLike;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RemoveShowLikeUseCaseTest {

    @Mock
    private ShowLikeRepository showLikeRepository;
    @Mock
    private MemberFinder memberFinder;
    @Mock
    private ShowFinder showFinder;
    @InjectMocks
    private RemoveShowLikeUseCase useCase;

    @Test
    void 찜이_존재하면_삭제후_false를_반환한다() {
        //given
        ShowLike showLike = mock(ShowLike.class);
        when(showLikeRepository.findByMemberIdAndShowId(1L, 2L)).thenReturn(Optional.of(showLike));
        when(showLikeRepository.countByShowId(2L)).thenReturn(4L);

        //when
        RemoveShowLikeUseCase.Output output = useCase.execute(new RemoveShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isFalse();
        assertThat(output.likeCount()).isEqualTo(4L);
        verify(showLikeRepository).delete(showLike);
    }

    @Test
    void 찜이_없어도_삭제없이_false를_반환한다() {
        //given
        when(showLikeRepository.findByMemberIdAndShowId(1L, 2L)).thenReturn(Optional.empty());
        when(showLikeRepository.countByShowId(2L)).thenReturn(0L);

        //when
        RemoveShowLikeUseCase.Output output = useCase.execute(new RemoveShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isFalse();
        assertThat(output.likeCount()).isZero();
        verify(showLikeRepository, never()).delete(any());
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void memberId_또는_showId가_없으면_예외를_던진다(final RemoveShowLikeUseCase.Input input) {
        //given
        //when
        //then
        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ApplicationErrorType.INVALID_INPUT));
    }

    private static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(new RemoveShowLikeUseCase.Input(null, 2L)),
                Arguments.of(new RemoveShowLikeUseCase.Input(1L, null))
        );
    }
}
