package com.ticket.security.auth;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialProvider;
import com.ticket.security.oauth.SocialAccountUnlinker;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class WithdrawCurrentMemberUseCaseTest {
    @Mock
    private MemberAccountApi memberAccountOperations;

    @Mock
    private SocialAccountUnlinker socialAccountUnlinker;

    @InjectMocks
    private WithdrawCurrentMemberUseCase useCase;

    @Test
    void 탈퇴_후_모든_카카오_계정을_연동해제한다() {
        // given
        final SocialAccountSnapshot first = new SocialAccountSnapshot(SocialProvider.KAKAO, "100");
        final SocialAccountSnapshot second = new SocialAccountSnapshot(SocialProvider.KAKAO, "200");
        when(memberAccountOperations.withdraw(5L)).thenReturn(List.of(first, second));
        // when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));
        // then
        verify(memberAccountOperations).withdraw(5L);
        verify(socialAccountUnlinker).unlink(first);
        verify(socialAccountUnlinker).unlink(second);
    }

    @Test
    void 카카오_연동해제_중_예외가_나도_탈퇴_흐름은_계속된다() {
        // given
        final SocialAccountSnapshot first = new SocialAccountSnapshot(SocialProvider.KAKAO, "100");
        final SocialAccountSnapshot second = new SocialAccountSnapshot(SocialProvider.GOOGLE, "200");
        when(memberAccountOperations.withdraw(5L)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("boom")).when(socialAccountUnlinker).unlink(first);
        // when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));
        // then
        verify(socialAccountUnlinker).unlink(first);
        verify(socialAccountUnlinker).unlink(second);
    }

    @Test
    void 연동해제할_카카오계정이_없으면_unlink를_호출하지_않는다() {
        // given
        when(memberAccountOperations.withdraw(5L)).thenReturn(List.of());
        // when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));
        // then
        verify(memberAccountOperations).withdraw(5L);
        verify(socialAccountUnlinker, never()).unlink(org.mockito.ArgumentMatchers.any());
    }
}
