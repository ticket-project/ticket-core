package com.ticket.member.account.application.usecase;

import com.ticket.member.account.application.MemberWithdrawalTransactionService;
import com.ticket.member.account.application.SocialAccountConnection;
import com.ticket.member.account.application.SocialAccountUnlinker;

import com.ticket.shared.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawCurrentMemberUseCase {

    private final MemberWithdrawalTransactionService memberWithdrawalTransactionService;
    private final SocialAccountUnlinker socialAccountUnlinker;

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
        final List<SocialAccountConnection> socialAccounts = memberWithdrawalTransactionService.withdraw(input.memberId);
        unlinkSocialAccountsSafely(input.memberId(), socialAccounts);
        return new Output();
    }

    private void unlinkSocialAccountsSafely(
            final Long memberId,
            final List<SocialAccountConnection> socialAccounts
    ) {
        socialAccounts.forEach(connection -> {
            try {
                socialAccountUnlinker.unlink(connection);
            } catch (Exception e) {
                log.warn("회원 탈퇴 후 소셜 연동 해제에 실패했습니다. memberId={}, provider={}",
                        memberId, connection.provider(), e);
            }
        });
    }
}
