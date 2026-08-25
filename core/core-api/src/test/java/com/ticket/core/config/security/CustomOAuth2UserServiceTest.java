package com.ticket.core.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import com.ticket.core.app.auth.oauth2.ProvisionOAuth2MemberUseCase;
import com.ticket.core.app.auth.oauth2.ProvisionedMember;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
class CustomOAuth2UserServiceTest {

    @Test
    void OAuth2_사용자정보를_회원에_연결하고_회원_식별자를_담은_주체를_반환한다() {
        ProvisionOAuth2MemberUseCase provisionUseCase = Mockito.mock(ProvisionOAuth2MemberUseCase.class);
        DefaultOAuth2UserService delegate = Mockito.mock(DefaultOAuth2UserService.class);
        CustomOAuth2UserService customOAuth2UserService = new CustomOAuth2UserService(provisionUseCase);
        ReflectionTestUtils.setField(customOAuth2UserService, "delegate", delegate);

        Map<String, Object> attributes = Map.of(
                "sub", "google-user-1",
                "email", "user@example.com",
                "name", "사용자"
        );
        OAuth2UserRequest userRequest = createUserRequest();
        OAuth2User oauth2User = new DefaultOAuth2User(java.util.List.of(), attributes, "sub");
        when(delegate.loadUser(userRequest)).thenReturn(oauth2User);
        when(provisionUseCase.execute(argThat(userInfo ->
                userInfo.providerId().equals("google-user-1")
                        && "user@example.com".equals(userInfo.email())
                        && "사용자".equals(userInfo.name())
        ))).thenReturn(new ProvisionedMember(7L, "MEMBER"));

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
