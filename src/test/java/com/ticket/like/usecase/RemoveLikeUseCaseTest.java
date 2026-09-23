package com.ticket.like.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.like.domain.Like;
import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RemoveLikeUseCaseTest {
    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private RemoveLikeUseCase useCase;

    @Test
    void 찜한_상태면_삭제하고_갱신된_찜수를_돌려준다() {
        Like like = new Like(1L, LikeType.SHOW, 2L);
        when(likeRepository.findByMemberIdAndLikeTypeAndTargetId(1L, LikeType.SHOW, 2L))
                .thenReturn(Optional.of(like));
        when(likeRepository.countByLikeTypeAndTargetId(LikeType.SHOW, 2L)).thenReturn(4L);

        RemoveLikeUseCase.Output output = useCase.execute(new RemoveLikeUseCase.Input(1L, LikeType.SHOW, 2L));

        assertThat(output.targetId()).isEqualTo(2L);
        assertThat(output.liked()).isFalse();
        assertThat(output.likeCount()).isEqualTo(4L);
        verify(likeRepository).delete(like);
    }

    @Test
    void 찜하지_않은_상태로_불러도_예외_없이_해제_상태를_돌려준다() {
        when(likeRepository.findByMemberIdAndLikeTypeAndTargetId(1L, LikeType.SHOW, 2L))
                .thenReturn(Optional.empty());
        when(likeRepository.countByLikeTypeAndTargetId(LikeType.SHOW, 2L)).thenReturn(4L);

        RemoveLikeUseCase.Output output = useCase.execute(new RemoveLikeUseCase.Input(1L, LikeType.SHOW, 2L));

        assertThat(output.liked()).isFalse();
        assertThat(output.likeCount()).isEqualTo(4L);
        verify(likeRepository, never()).delete(any());
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_targetId가_유효하지_않으면_Input_생성에서_예외를_던진다(
            final Long memberId, final Long targetId, final String message) {
        assertThatThrownBy(() -> new RemoveLikeUseCase.Input(memberId, LikeType.SHOW, targetId))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo(message);
    }

    @Test
    void likeType이_없으면_예외를_던진다() {
        assertThatThrownBy(() -> new RemoveLikeUseCase.Input(1L, null, 2L))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo("likeType는 필수입니다.");
    }

    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(NullPointerException.class);
    }

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(
                Arguments.of(null, 2L, "memberId는 필수입니다."),
                Arguments.of(1L, null, "targetId는 필수입니다."),
                Arguments.of(0L, 2L, "memberId는 양수여야 합니다."),
                Arguments.of(1L, -1L, "targetId는 양수여야 합니다."));
    }
}
