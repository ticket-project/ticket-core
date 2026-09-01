package com.ticket.core.config.security;

import com.ticket.core.app.auth.oauth2.ProvisionOAuth2MemberUseCase;
import com.ticket.core.app.auth.oauth2.ProvisionedMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /**
     * 회원 식별자를 담는 attribute 이름이다. DefaultOAuth2User의 nameAttributeKey로 지정해
     * Authentication.getName()이 회원 식별자를 돌려주게 한다.
     */
    static final String MEMBER_ID_ATTRIBUTE = "memberId";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final ProvisionOAuth2MemberUseCase provisionOAuth2MemberUseCase;

    @Override
    public OAuth2User loadUser(final OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        final OAuth2User oauth2User = delegate.loadUser(userRequest);
        final String registrationId = userRequest.getClientRegistration().getRegistrationId();

        final ProvisionedMember member =
                provisionOAuth2MemberUseCase.execute(registrationId, oauth2User.getAttributes());

        final Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put(MEMBER_ID_ATTRIBUTE, member.memberId());

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + member.role())),
                attributes,
                MEMBER_ID_ATTRIBUTE
        );
    }
}
