package com.ticket.member.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.MemberAccountOperations;
import com.ticket.member.MemberStatus;
import com.ticket.member.RawPassword;
import com.ticket.member.SocialAccountConnection;
import com.ticket.member.SocialIdentity;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * {@link MemberAccountOperations}의 member 소유 구현이다.
 *
 * <p>업무 규칙을 여기서 새로 쓰지 않는다 — 등록은 {@link MemberRegistrar}, 자격 증명 확인은 {@link CredentialAuthenticator},
 * 소셜 연결은 {@link OAuth2MemberProvisioningService}, 탈퇴는 {@link MemberWithdrawalTransactionService}가
 * 그대로 소유한다. 이 클래스가 하는 일은 그 결과를 entity가 아닌 공개 값({@link MemberStatus})으로 바꿔 내보내는 것뿐이다.
 */
@Service
@RequiredArgsConstructor
public class MemberAccountService implements MemberAccountOperations {
    private final MemberRegistrar memberRegistrar;
    private final CredentialAuthenticator credentialAuthenticator;
    private final OAuth2MemberProvisioningService oauth2MemberProvisioningService;
    private final MemberWithdrawalTransactionService memberWithdrawalTransactionService;
    private final MemberRepository memberRepository;

    @Override
    public Long register(final String email, final RawPassword password, final String name) {
        return memberRegistrar.register(Email.create(email), password, name);
    }

    @Override
    public MemberStatus authenticate(final String email, final RawPassword password) {
        return toStatus(credentialAuthenticator.authenticate(email, password));
    }

    @Override
    @Transactional(readOnly = true)
    public MemberStatus requireActiveIdentity(final long memberId) {
        return toStatus(
                memberRepository
                        .findActiveById(memberId)
                        .orElseThrow(() -> new NotFoundException()));
    }

    @Override
    public MemberStatus resolveSocialAccount(final SocialIdentity identity) {
        return toStatus(oauth2MemberProvisioningService.getOrCreateMember(identity));
    }

    @Override
    public List<SocialAccountConnection> withdraw(final long memberId) {
        return memberWithdrawalTransactionService.withdraw(memberId);
    }

    private MemberStatus toStatus(final Member member) {
        return new MemberStatus(member.getId(), !member.isDeleted(), member.getRole().name());
    }
}
