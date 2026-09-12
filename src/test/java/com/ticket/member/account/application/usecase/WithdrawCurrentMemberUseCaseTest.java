package com.ticket.member.account.application.usecase;

import com.ticket.member.account.application.MemberWithdrawalTransactionService;
import com.ticket.member.account.application.SocialAccountConnection;
import com.ticket.member.account.application.SocialAccountUnlinker;
import com.ticket.member.account.domain.SocialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class WithdrawCurrentMemberUseCaseTest {

    @Mock
    private MemberWithdrawalTransactionService memberWithdrawalTransactionService;

    @Mock
    private SocialAccountUnlinker socialAccountUnlinker;

    @InjectMocks
    private WithdrawCurrentMemberUseCase useCase;

    @Test
    void 탈퇴_후_모든_카카오_계정을_연동해제한다() {
        //given
        final SocialAccountConnection first = new SocialAccountConnection(SocialProvider.KAKAO, "100");
        final SocialAccountConnection second = new SocialAccountConnection(SocialProvider.KAKAO, "200");
        when(memberWithdrawalTransactionService.withdraw(5L)).thenReturn(List.of(first, second));

        //when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));

        //then
        verify(memberWithdrawalTransactionService).withdraw(5L);
        verify(socialAccountUnlinker).unlink(first);
        verify(socialAccountUnlinker).unlink(second);
    }

    @Test
    void 카카오_연동해제_중_예외가_나도_탈퇴_흐름은_계속된다() {
        //given
        final SocialAccountConnection first = new SocialAccountConnection(SocialProvider.KAKAO, "100");
        final SocialAccountConnection second = new SocialAccountConnection(SocialProvider.GOOGLE, "200");
        when(memberWithdrawalTransactionService.withdraw(5L)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("boom")).when(socialAccountUnlinker).unlink(first);

        //when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));

        //then
        verify(socialAccountUnlinker).unlink(first);
        verify(socialAccountUnlinker).unlink(second);
    }

    @Test
    void 연동해제할_카카오계정이_없으면_unlink를_호출하지_않는다() {
        //given
        when(memberWithdrawalTransactionService.withdraw(5L)).thenReturn(List.of());

        //when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));

        //then
        verify(memberWithdrawalTransactionService).withdraw(5L);
        verify(socialAccountUnlinker, never()).unlink(org.mockito.ArgumentMatchers.any());
    }
}

