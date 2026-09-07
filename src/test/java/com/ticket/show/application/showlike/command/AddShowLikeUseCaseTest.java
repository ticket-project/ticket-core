package com.ticket.show.application.showlike.command;

import com.ticket.show.ShowLookup;
import com.ticket.show.domain.showlike.repository.ShowLikeRepository;
import com.ticket.show.exception.ShowLikeAlreadyExistsException;
import com.ticket.error.InvalidRequestException;
import com.ticket.member.MemberLookup;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AddShowLikeUseCaseTest {

    @Mock
    private ShowLikeRepository showLikeRepository;
    @Mock
    private MemberLookup memberLookup;
    @Mock
    private ShowLookup showLookup;
    @InjectMocks
    private AddShowLikeUseCase useCase;

    @Test
    void 이미_찜한_공연이면_저장하지_않고_상태만_반환한다() {
        //given
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(true);
        when(showLikeRepository.countByShowId(2L)).thenReturn(5L);

        //when
        AddShowLikeUseCase.Output output = useCase.execute(new AddShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(5L);
        verify(memberLookup).requireActive(1L);
        verifyNoInteractions(showLookup);
    }

    @Test
    void 새로_찜하면_저장후_개수를_반환한다() {
        //given
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(false);
        when(showLikeRepository.countByShowId(2L)).thenReturn(3L);

        //when
        AddShowLikeUseCase.Output output = useCase.execute(new AddShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        verify(memberLookup).requireActive(1L);
        verify(showLookup).requireExisting(2L);
        verify(showLikeRepository).like(1L, 2L);
    }

    @Test
    void 저장중_중복제약이_발생하면_예외를_던진다() {
        //given
        when(showLikeRepository.existsByMemberIdAndShowId(1L, 2L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(showLikeRepository).like(1L, 2L);

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new AddShowLikeUseCase.Input(1L, 2L)))
                .isInstanceOf(ShowLikeAlreadyExistsException.class)
                .satisfies(exception -> assertThat(((ShowLikeAlreadyExistsException) exception).getData())
                        .isEqualTo("이미 찜한 공연입니다. memberId=1, showId=2"));
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
                .isInstanceOf(InvalidRequestException.class);
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
