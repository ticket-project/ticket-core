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
import java.util.Optional;

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
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(showRepository.existsById(2L)).thenReturn(true);
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(true);
        when(showLikeRepository.countByShowId(2L)).thenReturn(7L);

        //when
        GetShowLikeStatusUseCase.Output output = useCase.execute(new GetShowLikeStatusUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(7L);
        verify(showRepository).existsById(2L);
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_showId가_유효하지_않으면_Input_생성에서_예외를_던진다(
            final Long memberId,
            final Long showId
    ) {
        //given
        //when
        //then
        assertThatThrownBy(() -> new GetShowLikeStatusUseCase.Input(memberId, showId))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ApplicationErrorType.INVALID_INPUT));
    }

    /**
     * Input을 아예 넘기지 않은 것은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다.
     */
    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        //given
        //when
        //then
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(
                Arguments.of(null, 2L),
                Arguments.of(1L, null),
                Arguments.of(0L, 2L),
                Arguments.of(1L, -1L)
        );
    }
}
