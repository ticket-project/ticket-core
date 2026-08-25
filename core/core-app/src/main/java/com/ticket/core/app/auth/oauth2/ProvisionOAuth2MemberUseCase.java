package com.ticket.core.app.auth.oauth2;

import com.ticket.core.domain.member.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 사용자 정보를 회원에 연결하고 식별 값만 돌려준다.
 * 회원 엔티티가 API 계층으로 넘어가지 않도록 경계에서 값으로 바꾸는 것이 이 use case의 역할이다.
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
