package com.ticket.core.config.security;

import com.ticket.core.app.auth.oauth2.OAuth2MemberProvisioningService;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.Role;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
class CustomOAuth2UserServiceTest {

    @Test
    void OAuth2_사용자정보를_회원에_연결하고_MemberPrincipal을_반환한다() {
        OAuth2MemberProvisioningService provisioningService = Mockito.mock(OAuth2MemberProvisioningService.class);
        DefaultOAuth2UserService delegate = Mockito.mock(DefaultOAuth2UserService.class);
        CustomOAuth2UserService customOAuth2UserService = new CustomOAuth2UserService(provisioningService);
        ReflectionTestUtils.setField(customOAuth2UserService, "delegate", delegate);

        Map<String, Object> attributes = Map.of(
                "sub", "google-user-1",
                "email", "user@example.com",
                "name", "사용자"
        );
        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User oauth2User = new DefaultOAuth2User(java.util.List.of(), attributes, "sub");
        Member member = Member.createSocialMember(Email.create("user@example.com"), "사용자", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 7L);

        when(delegate.loadUser(userRequest)).thenReturn(oauth2User);
        when(provisioningService.getOrCreateMember(argThat(userInfo ->
                userInfo.providerId().equals("google-user-1")
                        && "user@example.com".equals(userInfo.email())
                        && "사용자".equals(userInfo.name())
        ))).thenReturn(member);

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        assertThat(result).isInstanceOf(MemberPrincipal.class);
        MemberPrincipal principal = (MemberPrincipal) result;
        assertThat(principal.getMemberId()).isEqualTo(7L);
        assertThat(principal.getRole()).isEqualTo("MEMBER");
        assertThat(principal.getAttributes()).containsEntry("email", "user@example.com");
        verify(delegate).loadUser(userRequest);
    }

    private OAuth2UserRequest createUserRequest() {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(300)
        );
        return new OAuth2UserRequest(clientRegistration, accessToken);
    }
}
