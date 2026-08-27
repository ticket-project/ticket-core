package com.ticket.core.app.member.command;

import com.ticket.core.app.auth.oauth2.KakaoUnlinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawCurrentMemberUseCase {

    private final MemberWithdrawalTxService memberWithdrawalTxService;
    private final KakaoUnlinkService kakaoUnlinkService;

    public record Input(Long memberId) {
        public Input {
            RequiredInput.positiveId(memberId, "memberId");
        }
    }
    public record Output() {}

    public Output execute(final Input input) {
        final List<String> kakaoSocialIds = memberWithdrawalTxService.withdraw(input.memberId);
        unlinkKakaoAccountsSafely(input.memberId(), kakaoSocialIds);
        return new Output();
    }

    private void unlinkKakaoAccountsSafely(final Long memberId, final List<String> kakaoSocialIds) {
        kakaoSocialIds.forEach(socialId -> {
            try {
                kakaoUnlinkService.unlinkByUserId(socialId);
            } catch (Exception e) {
                log.warn("회원 탈퇴 후 카카오 연동 해제에 실패했습니다. memberId={}, provider=kakao", memberId, e);
            }
        });
    }
}
