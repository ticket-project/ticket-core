package com.ticket.core.app.showlike.query;

import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.app.support.cursor.CursorPage;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetMyShowLikesUseCaseTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ShowLikeReadRepository showLikeReadRepository;

    @InjectMocks
    private GetMyShowLikesUseCase useCase;

    @Test
    void 찜한_공연_목록을_다음_커서_위치와_함께_조회한다() {
        Member member = mock(Member.class);
        GetMyShowLikesUseCase.ShowLikeSummary summary = new GetMyShowLikesUseCase.ShowLikeSummary(
                2L,
                "공연",
                "image",
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                "장소",
                LocalDateTime.now()
        );
        when(member.getId()).thenReturn(1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(showLikeReadRepository.findMyLikedShows(1L, 10L, 20))
                .thenReturn(new CursorPage<>(List.of(summary), true, 9L));

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, 10L, 20));

        assertThat(output.items()).containsExactly(summary);
        assertThat(output.hasNext()).isTrue();
        assertThat(output.nextPosition()).isEqualTo(9L);
        verify(showLikeReadRepository).findMyLikedShows(1L, 10L, 20);
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void memberId나_size가_유효하지_않으면_Input_생성에서_예외를_던진다(
            final Long memberId,
            final int size
    ) {
        assertThatThrownBy(() -> new GetMyShowLikesUseCase.Input(memberId, null, size))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ApplicationErrorType.INVALID_INPUT));
    }

    /**
     * Input을 아예 넘기지 않은 것은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다.
     */
    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 커서_위치가_없으면_첫_페이지를_조회한다() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(showLikeReadRepository.findMyLikedShows(1L, null, 20))
                .thenReturn(CursorPage.empty());

        GetMyShowLikesUseCase.Output output = useCase.execute(new GetMyShowLikesUseCase.Input(1L, null, 20));

        assertThat(output.items()).isEmpty();
        assertThat(output.hasNext()).isFalse();
        assertThat(output.nextPosition()).isNull();
        verify(showLikeReadRepository).findMyLikedShows(1L, null, 20);
    }

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(
                Arguments.of(null, 20),
                Arguments.of(0L, 20),
                Arguments.of(1L, 0),
                Arguments.of(1L, 101)
        );
    }
}
