package com.ticket.member.oauth.application.usecase;

import com.ticket.member.oauth.application.OAuth2MemberProvisioningService;
import com.ticket.member.oauth.application.ProvisionedMember;

import com.ticket.member.oauth.domain.OAuth2UserInfo;
import com.ticket.member.oauth.domain.OAuth2UserInfoFactory;
import com.ticket.member.account.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 소셜 제공자가 준 사용자 정보를 해석해 회원에 연결하고 식별 값만 돌려준다.
 *
 * <p>제공자별 응답 해석과 회원 엔티티는 API 계층으로 넘기지 않는다. 컨트롤러 쪽은
 * registrationId와 원본 attributes만 넘기면 된다.
 */
@Service
@RequiredArgsConstructor
public class ProvisionOAuth2MemberUseCase {

    private final OAuth2MemberProvisioningService oauth2MemberProvisioningService;

    public ProvisionedMember execute(final String registrationId, final Map<String, Object> attributes) {
        final OAuth2UserInfo userInfo = OAuth2UserInfoFactory.create(registrationId, attributes);
        final Member member = oauth2MemberProvisioningService.getOrCreateMember(userInfo);
        return new ProvisionedMember(member.getId(), member.getRole().name());
    }
}
