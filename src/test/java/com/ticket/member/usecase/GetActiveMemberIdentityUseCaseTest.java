package com.ticket.member.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.member.domain.MemberRepository;
import com.ticket.shared.exception.NotFoundException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetActiveMemberIdentityUseCaseTest {
    @Mock
    private MemberRepository memberRepository;

    @Test
    void 활성_확인은_없는_회원이면_찾을_수_없다는_실패를_낸다() {
        when(memberRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetActiveMemberIdentityUseCase(memberRepository).execute(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
