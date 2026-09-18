package com.ticket.security.auth;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.SocialAccountConnection;
import com.ticket.security.oauth.SocialAccountUnlinker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawCurrentMemberUseCase {
    private final MemberAccountApi memberAccountOperations;
    private final SocialAccountUnlinker socialAccountUnlinker;

    public record Input(Long memberId) {
        public Input {
            requirePositiveId(memberId, "memberId");
        }
    }

    public record Output() {}

    public Output execute(final Input input) {
        final List<SocialAccountConnection> socialAccounts =
                memberAccountOperations.withdraw(input.memberId());
        unlinkSocialAccountsSafely(input.memberId(), socialAccounts);
        return new Output();
    }

    private void unlinkSocialAccountsSafely(
            final Long memberId, final List<SocialAccountConnection> socialAccounts) {
        socialAccounts.forEach(
                connection -> {
                    try {
                        socialAccountUnlinker.unlink(connection);
                    } catch (Exception e) {
                        log.warn(
                                "회원 탈퇴 후 소셜 연동 해제에 실패했습니다. memberId={}, provider={}",
                                memberId,
                                connection.provider(),
                                e);
                    }
                });
    }
}
