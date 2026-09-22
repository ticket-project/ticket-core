package com.ticket.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.api.SocialProvider;

@SuppressWarnings("NonAsciiCharacters")
class CustomOAuth2UserServiceTest {
    @Test
    void OAuth2_사용자정보를_회원에_연결하고_회원_식별자를_담은_주체를_반환한다() {
        MemberAccountApi memberAccountApi = Mockito.mock(MemberAccountApi.class);
        DefaultOAuth2UserService delegate = Mockito.mock(DefaultOAuth2UserService.class);
        CustomOAuth2UserService customOAuth2UserService = new CustomOAuth2UserService(memberAccountApi);
        ReflectionTestUtils.setField(customOAuth2UserService, "delegate", delegate);

        Map<String, Object> attributes =
                Map.of("sub", "google-user-1", "email", "user@example.com", "email_verified", true, "name", "사용자");
        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User oauth2User = new DefaultOAuth2User(java.util.List.of(), attributes, "sub");
        when(delegate.loadUser(userRequest)).thenReturn(oauth2User);
        final SocialIdentity userInfo =
                new SocialIdentity(SocialProvider.GOOGLE, "google-user-1", "user@example.com", true, "사용자");
        when(memberAccountApi.resolveSocialAccount(userInfo)).thenReturn(new MemberStatus(7L, true, "MEMBER"));

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);
        // getName()이 회원 식별자를 돌려주어야 로그인 성공 처리에서 auth code를 만들 수 있다.
        assertThat(result.getName()).isEqualTo("7");
        assertThat(result.<Long>getAttribute("memberId")).isEqualTo(7L);
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
        // 제공자가 준 정보는 버리지 않는다.
        assertThat(result.getAttributes()).containsEntry("email", "user@example.com");
        verify(delegate).loadUser(userRequest);
        verify(memberAccountApi).resolveSocialAccount(userInfo);
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
                Instant.now().plusSeconds(300));
        return new OAuth2UserRequest(clientRegistration, accessToken);
    }
}
