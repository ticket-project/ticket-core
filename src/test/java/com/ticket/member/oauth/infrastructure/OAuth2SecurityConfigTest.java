package com.ticket.member.oauth.infrastructure;

import com.ticket.TicketApplication;
import com.ticket.member.AccessTokenReader;
import com.ticket.security.infrastructure.ApiSecurityConfig;
import com.ticket.security.infrastructure.RestAccessDeniedHandler;
import com.ticket.security.infrastructure.RestAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OAuth2SecurityConfigTest.TestController.class)
@ContextConfiguration(classes = TicketApplication.class)
@Import({
        OAuth2SecurityConfig.class,
        ApiSecurityConfig.class,
        OAuth2SecurityConfigTest.ClientRegistrationConfig.class,
        OAuth2SecurityConfigTest.TestController.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000")
@SuppressWarnings("NonAsciiCharacters")
class OAuth2SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2FrontendRedirectResolver frontendRedirectResolver;

    @MockitoBean
    private OAuth2AuthenticationSuccessHandler authenticationSuccessHandler;

    @MockitoBean
    private OAuth2AuthenticationFailureHandler authenticationFailureHandler;

    @MockitoBean
    private AccessTokenReader accessTokenReader;

    @MockitoBean
    private RestAuthenticationEntryPoint authenticationEntryPoint;

    @MockitoBean
    private RestAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.doAnswer(invocation -> {
            final HttpServletResponse response = invocation.getArgument(1);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(authenticationEntryPoint).commence(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void OAuth2_authorization_경로는_Order_1_chain이_처리해_provider로_redirect한다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/authorize/google")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        startsWith("https://accounts.example/authorize?")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getRequest().getSession(false)).isNotNull());

        verify(frontendRedirectResolver).storeFrontendBaseUrl(Mockito.any());
    }

    @Test
    void OAuth2_외_보호_api는_Order_2_chain이_인증을_요구한다() throws Exception {
        mockMvc.perform(get("/api/v1/private-test"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/private-test")
        String privateApi() {
            return "private";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClientRegistrationConfig {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            final ClientRegistration google = ClientRegistration.withRegistrationId("google")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/api/v1/auth/oauth2/callback/{registrationId}")
                    .authorizationUri("https://accounts.example/authorize")
                    .tokenUri("https://accounts.example/token")
                    .userInfoUri("https://accounts.example/userinfo")
                    .userNameAttributeName("sub")
                    .scope("openid", "profile", "email")
                    .build();
            return new InMemoryClientRegistrationRepository(google);
        }
    }
}
