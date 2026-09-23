package com.ticket.like.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;
import com.ticket.shared.exception.InvalidRequestException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetLikeStatusUseCaseTest {
    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private GetLikeStatusUseCase useCase;

    @Test
    void 찜_상태와_총_찜수를_반환한다() {
        when(likeRepository.existsByMemberIdAndLikeTypeAndTargetId(1L, LikeType.SHOW, 2L))
                .thenReturn(true);
        when(likeRepository.countByLikeTypeAndTargetId(LikeType.SHOW, 2L)).thenReturn(7L);

        GetLikeStatusUseCase.Output output = useCase.execute(new GetLikeStatusUseCase.Input(1L, LikeType.SHOW, 2L));

        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(7L);
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_targetId가_유효하지_않으면_Input_생성에서_예외를_던진다(final Long memberId, final Long targetId) {
        assertThatThrownBy(() -> new GetLikeStatusUseCase.Input(memberId, LikeType.SHOW, targetId))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(Arguments.of(null, 2L), Arguments.of(1L, null), Arguments.of(0L, 2L), Arguments.of(1L, -1L));
    }
}
