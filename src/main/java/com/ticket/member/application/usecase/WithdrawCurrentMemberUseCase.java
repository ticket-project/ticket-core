package com.ticket.member.application.usecase;

import com.ticket.member.application.MemberWithdrawalTransactionService;

import com.ticket.error.InvalidRequestException;
import com.ticket.member.oauth.application.KakaoUnlinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawCurrentMemberUseCase {

    private final MemberWithdrawalTransactionService memberWithdrawalTransactionService;
    private final KakaoUnlinkService kakaoUnlinkService;

    public record Input(Long memberId) {
        public Input {
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }
    public record Output() {}

    public Output execute(final Input input) {
        final List<String> kakaoSocialIds = memberWithdrawalTransactionService.withdraw(input.memberId);
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
