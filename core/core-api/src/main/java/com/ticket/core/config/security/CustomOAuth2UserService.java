package com.ticket.core.config.security;

import com.ticket.core.app.auth.oauth2.OAuth2MemberProvisioningService;
import com.ticket.core.app.auth.oauth2.OAuth2UserInfo;
import com.ticket.core.app.auth.oauth2.OAuth2UserInfoFactory;
import com.ticket.core.domain.member.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final OAuth2MemberProvisioningService oauth2MemberProvisioningService;

    @Override
    public OAuth2User loadUser(final OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        final OAuth2User oauth2User = delegate.loadUser(userRequest);
        final String registrationId = userRequest.getClientRegistration().getRegistrationId();
        final OAuth2UserInfo userInfo = OAuth2UserInfoFactory.create(registrationId, oauth2User.getAttributes());

        final Member member = oauth2MemberProvisioningService.getOrCreateMember(userInfo);
        return new MemberPrincipal(member.getId(), member.getRole().name(), oauth2User.getAttributes());
    }
}
