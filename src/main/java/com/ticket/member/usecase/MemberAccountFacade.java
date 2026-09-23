package com.ticket.member.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.domain.Member;

import lombok.RequiredArgsConstructor;

/** 공개 계정 계약을 각 연산의 트랜잭션과 정책을 소유한 유스케이스에 연결한다. */
@Service
@RequiredArgsConstructor
public class MemberAccountFacade implements MemberAccountApi {
    private final RegisterMemberUseCase registerMemberUseCase;
    private final AuthenticateMemberUseCase authenticateMemberUseCase;
    private final GetActiveMemberIdentityUseCase getActiveMemberIdentityUseCase;
    private final OAuth2MemberProvisioningService oauth2MemberProvisioningService;
    private final WithdrawMemberUseCase withdrawMemberUseCase;

    @Override
    public Long register(final String email, final RawPassword password, final String name) {
        return registerMemberUseCase.execute(email, password, name);
    }

    @Override
    public MemberStatus authenticate(final String email, final RawPassword password) {
        return authenticateMemberUseCase.execute(email, password);
    }

    @Override
    public MemberStatus getActiveIdentity(final long memberId) {
        return getActiveMemberIdentityUseCase.execute(memberId);
    }

    @Override
    public MemberStatus resolveSocialAccount(final SocialIdentity identity) {
        final Member member = oauth2MemberProvisioningService.getOrCreateMember(identity);
        return new MemberStatus(
                member.getId(), !member.isDeleted(), member.getRole().name());
    }

    @Override
    public List<SocialAccountSnapshot> withdraw(final long memberId) {
        return withdrawMemberUseCase.execute(memberId);
    }
}
