package com.ticket.show.application.showlike.command;

import com.ticket.show.domain.show.repository.ShowRepository;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.favorite.ShowLikeCommand;
import com.ticket.favorite.ShowLikeInfo;
import com.ticket.member.MemberLookup;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AddShowLikeUseCaseTest {

    @Mock
    private MemberLookup memberLookup;
    @Mock
    private ShowRepository showRepository;
    @Mock
    private ShowLikeCommand showLikeCommand;
    @InjectMocks
    private AddShowLikeUseCase useCase;

    @Test
    void 공연이_존재하면_favorite에_찜을_위임한다() {
        //given
        when(showRepository.existsById(2L)).thenReturn(true);
        when(showLikeCommand.like(1L, 2L)).thenReturn(new ShowLikeInfo(true, 5L));

        //when
        AddShowLikeUseCase.Output output = useCase.execute(new AddShowLikeUseCase.Input(1L, 2L));

        //then
        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(5L);
        verify(memberLookup).requireActive(1L);
        verify(showLikeCommand).like(1L, 2L);
    }

    @Test
    void 공연이_없으면_favorite를_부르지_않고_예외를_던진다() {
        //given
        when(showRepository.existsById(2L)).thenReturn(false);

        //when
        //then
        assertThatThrownBy(() -> useCase.execute(new AddShowLikeUseCase.Input(1L, 2L)))
                .isInstanceOf(NotFoundException.class);
        verify(showLikeCommand, never()).like(anyLong(), anyLong());
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
