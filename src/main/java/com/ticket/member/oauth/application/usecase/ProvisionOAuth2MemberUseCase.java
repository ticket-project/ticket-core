package com.ticket.member.oauth.application.usecase;

import org.springframework.stereotype.Service;

import com.ticket.member.MemberAccountOperations;
import com.ticket.member.MemberStatus;
import com.ticket.member.SocialIdentity;
import com.ticket.member.oauth.application.ProvisionedMember;

import lombok.RequiredArgsConstructor;

/**
 * provider adapter가 정규화한 소셜 신원을 회원에 연결하고 식별 값만 돌려준다.
 *
 * <p>제공자별 원본 응답 형식과 회원 엔티티를 경계 밖으로 넘기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProvisionOAuth2MemberUseCase {
    private final MemberAccountOperations memberAccountOperations;

    public ProvisionedMember execute(final SocialIdentity userInfo) {
        final MemberStatus member = memberAccountOperations.resolveSocialAccount(userInfo);
        return new ProvisionedMember(member.memberId(), member.role());
    }
}
