package com.ticket.core.app.showlike.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.show.repository.ShowRepository;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetShowLikeStatusUseCaseTest {

    @Mock
    private ShowLikeRepository showLikeRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ShowRepository showRepository;
    @InjectMocks
    private GetShowLikeStatusUseCase useCase;

    @Test
    void 찜_상태와_총_찜수를_반환한다() {
        //given
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberRepository.getActiveById(1L)).thenReturn(member);
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(true);
        when(showLikeRepository.countByShowId(2L)).thenReturn(7L);

        //when
        GetShowLikeStatusUseCase.Output output = useCase.execute(new GetShowLikeStatusUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(7L);
        verify(showRepository).requireExists(2L);
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void memberId_또는_showId가_없으면_예외를_던진다(final GetShowLikeStatusUseCase.Input input) {
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
                Arguments.of(new GetShowLikeStatusUseCase.Input(null, 2L)),
                Arguments.of(new GetShowLikeStatusUseCase.Input(1L, null))
        );
    }
}
