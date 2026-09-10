package com.ticket.like.preference.application.usecase;

import com.ticket.error.InvalidRequestException;
import com.ticket.like.LikeCommand;
import com.ticket.like.LikeInfo;
import com.ticket.like.LikeType;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AddLikeUseCaseTest {

    @Mock
    private MemberLookup memberLookup;
    @Mock
    private LikeCommand likeCommand;
    @InjectMocks
    private AddLikeUseCase useCase;

    @Test
    void 회원이_활성이면_like에_찜을_위임한다() {
        when(likeCommand.like(1L, LikeType.SHOW, 2L)).thenReturn(new LikeInfo(true, 5L));

        AddLikeUseCase.Output output = useCase.execute(new AddLikeUseCase.Input(1L, LikeType.SHOW, 2L));

        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(5L);
        assertThat(output.targetId()).isEqualTo(2L);
        verify(memberLookup).requireActive(1L);
        verify(likeCommand).like(1L, LikeType.SHOW, 2L);
    }

    @Test
    void 존재하지_않는_대상이어도_존재_확인_없이_그대로_저장한다() {
        when(likeCommand.like(1L, LikeType.SHOW, 999L)).thenReturn(new LikeInfo(true, 1L));

        AddLikeUseCase.Output output = useCase.execute(new AddLikeUseCase.Input(1L, LikeType.SHOW, 999L));

        assertThat(output.liked()).isTrue();
        verify(likeCommand).like(1L, LikeType.SHOW, 999L);
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_targetId가_유효하지_않으면_Input_생성에서_예외를_던진다(
            final Long memberId,
            final Long targetId
    ) {
        assertThatThrownBy(() -> new AddLikeUseCase.Input(memberId, LikeType.SHOW, targetId))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void likeType이_없으면_예외를_던진다() {
        assertThatThrownBy(() -> new AddLikeUseCase.Input(1L, null, 2L))
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

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(
                Arguments.of(null, 2L),
                Arguments.of(1L, null),
                Arguments.of(0L, 2L),
                Arguments.of(1L, -1L)
        );
    }
}
