package com.ticket.member.oauth.application.usecase;

import com.ticket.member.oauth.application.OAuth2MemberProvisioningService;
import com.ticket.member.oauth.application.ProvisionedMember;

import com.ticket.member.oauth.domain.OAuth2UserInfo;
import com.ticket.member.account.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * provider adapter가 정규화한 소셜 신원을 회원에 연결하고 식별 값만 돌려준다.
 *
 * <p>제공자별 원본 응답 형식과 회원 엔티티를 경계 밖으로 넘기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProvisionOAuth2MemberUseCase {

    private final OAuth2MemberProvisioningService oauth2MemberProvisioningService;

    public ProvisionedMember execute(final OAuth2UserInfo userInfo) {
        final Member member = oauth2MemberProvisioningService.getOrCreateMember(userInfo);
        return new ProvisionedMember(member.getId(), member.getRole().name());
    }
}
