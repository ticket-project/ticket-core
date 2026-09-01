package com.ticket.core.app.showlike.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.show.model.Show;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AddShowLikeUseCaseTest {

    @Mock
    private ShowLikeRepository showLikeRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ShowRepository showRepository;
    @InjectMocks
    private AddShowLikeUseCase useCase;

    @Test
    void 이미_찜한_공연이면_저장하지_않고_상태만_반환한다() {
        //given
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(true);
        when(showLikeRepository.countByShowId(2L)).thenReturn(5L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(mock(Member.class)));

        //when
        AddShowLikeUseCase.Output output = useCase.execute(new AddShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(5L);
    }

    @Test
    void 새로_찜하면_저장후_개수를_반환한다() {
        //given
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(false);
        when(showLikeRepository.countByShowId(2L)).thenReturn(3L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(showRepository.findById(2L)).thenReturn(Optional.of(mock(Show.class)));

        //when
        AddShowLikeUseCase.Output output = useCase.execute(new AddShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        verify(showLikeRepository).save(any());
    }

    @Test
    void 저장중_중복제약이_발생하면_예외를_던진다() {
        //given
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(false);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(showRepository.findById(2L)).thenReturn(Optional.of(mock(Show.class)));
        when(showLikeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new AddShowLikeUseCase.Input(1L, 2L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.SHOW_LIKE_ALREADY_EXISTS));
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
        assertThatThrownBy(() -> new AddShowLikeUseCase.Input(memberId, showId))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
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
