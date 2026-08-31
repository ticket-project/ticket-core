package com.ticket.core.app.member.command;

import com.ticket.core.app.auth.oauth2.KakaoUnlinkService;
import com.ticket.core.app.member.command.MemberWithdrawalTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
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
    private KakaoUnlinkService kakaoUnlinkService;

    @InjectMocks
    private WithdrawCurrentMemberUseCase useCase;

    @Test
    void 탈퇴_후_모든_카카오_계정을_연동해제한다() {
        //given
        when(memberWithdrawalTransactionService.withdraw(5L)).thenReturn(List.of("100", "200"));

        //when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));

        //then
        verify(memberWithdrawalTransactionService).withdraw(5L);
        verify(kakaoUnlinkService).unlinkByUserId("100");
        verify(kakaoUnlinkService).unlinkByUserId("200");
    }

    @Test
    void 카카오_연동해제_중_예외가_나도_탈퇴_흐름은_계속된다() {
        //given
        when(memberWithdrawalTransactionService.withdraw(5L)).thenReturn(List.of("100", "200"));
        doThrow(new IllegalStateException("boom")).when(kakaoUnlinkService).unlinkByUserId("100");

        //when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));

        //then
        verify(kakaoUnlinkService).unlinkByUserId("100");
        verify(kakaoUnlinkService).unlinkByUserId("200");
    }

    @Test
    void 연동해제할_카카오계정이_없으면_unlink를_호출하지_않는다() {
        //given
        when(memberWithdrawalTransactionService.withdraw(5L)).thenReturn(List.of());

        //when
        useCase.execute(new WithdrawCurrentMemberUseCase.Input(5L));

        //then
        verify(memberWithdrawalTransactionService).withdraw(5L);
        verify(kakaoUnlinkService, never()).unlinkByUserId(anyString());
    }
}

