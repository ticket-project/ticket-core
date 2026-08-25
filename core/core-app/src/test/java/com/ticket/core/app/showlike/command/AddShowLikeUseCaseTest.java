package com.ticket.core.domain.showlike.command;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.query.ShowFinder;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AddShowLikeUseCaseTest {

    @Mock
    private ShowLikeRepository showLikeRepository;
    @Mock
    private MemberFinder memberFinder;
    @Mock
    private ShowFinder showFinder;
    @InjectMocks
    private AddShowLikeUseCase useCase;

    @Test
    void 이미_찜한_공연이면_저장하지_않고_상태만_반환한다() {
        //given
        when(showLikeRepository.existsByMember_IdAndShow_Id(1L, 2L)).thenReturn(true);
        when(showLikeRepository.countByShow_Id(2L)).thenReturn(5L);
        when(memberFinder.findActiveMemberById(1L)).thenReturn(mock(Member.class));

        //when
        AddShowLikeUseCase.Output output = useCase.execute(new AddShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(5L);
    }

    @Test
    void 새로_찜하면_저장후_개수를_반환한다() {
        //given
        when(showLikeRepository.existsByMember_IdAndShow_Id(1L, 2L)).thenReturn(false);
        when(showLikeRepository.countByShow_Id(2L)).thenReturn(3L);
        when(memberFinder.findActiveMemberById(1L)).thenReturn(mock(Member.class));
        when(showFinder.findById(2L)).thenReturn(mock(Show.class));

        //when
        AddShowLikeUseCase.Output output = useCase.execute(new AddShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        verify(showLikeRepository).save(any());
    }

    @Test
    void 저장중_중복제약이_발생하면_예외를_던진다() {
        //given
        when(showLikeRepository.existsByMember_IdAndShow_Id(1L, 2L)).thenReturn(false);
        when(memberFinder.findActiveMemberById(1L)).thenReturn(mock(Member.class));
        when(showFinder.findById(2L)).thenReturn(mock(Show.class));
        when(showLikeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new AddShowLikeUseCase.Input(1L, 2L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.SHOW_LIKE_ALREADY_EXISTS));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void memberId_또는_showId가_없으면_예외를_던진다(final AddShowLikeUseCase.Input input) {
        //given
        //when
        //then
        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    private static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(new AddShowLikeUseCase.Input(null, 2L)),
                Arguments.of(new AddShowLikeUseCase.Input(1L, null))
        );
    }
}
