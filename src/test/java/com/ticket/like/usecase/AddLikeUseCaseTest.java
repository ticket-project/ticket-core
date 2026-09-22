package com.ticket.like.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.dao.DataIntegrityViolationException;

import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;
import com.ticket.like.exception.LikeAlreadyExistsException;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AddLikeUseCaseTest {
    @Mock
    private MemberLookupApi memberLookup;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private AddLikeUseCase useCase;

    @Test
    void 처음_찜하면_저장하고_갱신된_찜수를_돌려준다() {
        when(likeRepository.existsByMemberIdAndLikeTypeAndTargetId(1L, LikeType.SHOW, 2L))
                .thenReturn(false);
        when(likeRepository.countByLikeTypeAndTargetId(LikeType.SHOW, 2L)).thenReturn(5L);

        AddLikeUseCase.Output output = useCase.execute(new AddLikeUseCase.Input(1L, LikeType.SHOW, 2L));

        assertThat(output.targetId()).isEqualTo(2L);
        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(5L);
        verify(memberLookup).requireActive(1L);
        verify(likeRepository).like(1L, LikeType.SHOW, 2L);
    }

    @Test
    void 이미_찜한_상태면_다시_저장하지_않고_같은_상태를_돌려준다() {
        when(likeRepository.existsByMemberIdAndLikeTypeAndTargetId(1L, LikeType.SHOW, 2L))
                .thenReturn(true);
        when(likeRepository.countByLikeTypeAndTargetId(LikeType.SHOW, 2L)).thenReturn(5L);

        AddLikeUseCase.Output output = useCase.execute(new AddLikeUseCase.Input(1L, LikeType.SHOW, 2L));

        assertThat(output.liked()).isTrue();
        assertThat(output.likeCount()).isEqualTo(5L);
        verify(likeRepository, never()).like(anyLong(), any(), anyLong());
    }

    /**
     * 실제 동시 요청을 재현하지 않는다 — repository가 무결성 위반을 던졌을 때의 <b>예외 매핑</b>만 본다. 그 위반이 실제로 중복 찜에서만 생긴다는 사실은
     * {@code LikeRepositoryPagingTest}가 실제 DB로 고정한다.
     */
    @Test
    void unique_제약을_위반하면_찜_중복_예외로_바꿔_던진다() {
        when(likeRepository.existsByMemberIdAndLikeTypeAndTargetId(1L, LikeType.SHOW, 2L))
                .thenReturn(false);
        when(likeRepository.like(1L, LikeType.SHOW, 2L))
                .thenThrow(new DataIntegrityViolationException("UK_LIKES_MEMBER_TARGET"));

        assertThatThrownBy(() -> useCase.execute(new AddLikeUseCase.Input(1L, LikeType.SHOW, 2L)))
                .isInstanceOf(LikeAlreadyExistsException.class);
    }

    @Test
    void 존재하지_않는_대상이어도_존재_확인_없이_그대로_저장한다() {
        when(likeRepository.existsByMemberIdAndLikeTypeAndTargetId(1L, LikeType.SHOW, 999L))
                .thenReturn(false);
        when(likeRepository.countByLikeTypeAndTargetId(LikeType.SHOW, 999L)).thenReturn(1L);

        AddLikeUseCase.Output output = useCase.execute(new AddLikeUseCase.Input(1L, LikeType.SHOW, 999L));

        assertThat(output.liked()).isTrue();
        verify(likeRepository).like(1L, LikeType.SHOW, 999L);
    }

    @Test
    void 탈퇴_회원이면_저장하지_않는다() {
        doThrow(new NotFoundException("탈퇴한 회원입니다.")).when(memberLookup).requireActive(1L);

        assertThatThrownBy(() -> useCase.execute(new AddLikeUseCase.Input(1L, LikeType.SHOW, 2L)))
                .isInstanceOf(NotFoundException.class);

        verify(likeRepository, never()).like(anyLong(), any(), anyLong());
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_targetId가_유효하지_않으면_Input_생성에서_예외를_던진다(
            final Long memberId, final Long targetId, final String message) {
        assertThatThrownBy(() -> new AddLikeUseCase.Input(memberId, LikeType.SHOW, targetId))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo(message);
    }

    @Test
    void likeType이_없으면_예외를_던진다() {
        assertThatThrownBy(() -> new AddLikeUseCase.Input(1L, null, 2L))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getData())
                .isEqualTo("likeType는 필수입니다.");
    }

    /** Input을 아예 넘기지 않은 것은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다. */
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
