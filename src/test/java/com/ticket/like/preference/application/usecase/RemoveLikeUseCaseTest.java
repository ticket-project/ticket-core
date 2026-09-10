package com.ticket.like.preference.application.usecase;

import com.ticket.shared.exception.InvalidRequestException;
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
class RemoveLikeUseCaseTest {

    @Mock
    private MemberLookup memberLookup;
    @Mock
    private LikeCommand likeCommand;
    @InjectMocks
    private RemoveLikeUseCase useCase;

    @Test
    void 회원이_활성이면_like에_찜_해제를_위임한다() {
        when(likeCommand.unlike(1L, LikeType.SHOW, 2L)).thenReturn(new LikeInfo(false, 4L));

        RemoveLikeUseCase.Output output = useCase.execute(new RemoveLikeUseCase.Input(1L, LikeType.SHOW, 2L));

        assertThat(output.liked()).isFalse();
        assertThat(output.likeCount()).isEqualTo(4L);
        verify(memberLookup).requireActive(1L);
        verify(likeCommand).unlike(1L, LikeType.SHOW, 2L);
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_targetId가_유효하지_않으면_Input_생성에서_예외를_던진다(
            final Long memberId,
            final Long targetId
    ) {
        assertThatThrownBy(() -> new RemoveLikeUseCase.Input(memberId, LikeType.SHOW, targetId))
                .isInstanceOf(InvalidRequestException.class);
    }

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
