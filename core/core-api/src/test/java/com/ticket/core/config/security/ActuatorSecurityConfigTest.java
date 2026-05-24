package com.ticket.core.config.security;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActuatorSecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, ActuatorSecurityConfigTest.TestController.class})
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "app.cors.allowed-origins=http://localhost:3000",
        "security.jwt.secret-key=12345678901234567890123456789012",
        "security.jwt.access-token-expiration-seconds=1800",
        "security.jwt.refresh-token-expiration-seconds=1209600",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "spring.security.oauth2.client.registration.kakao.client-id=test-kakao-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-kakao-client-secret",
        "app.auth.oauth2-success-redirect-uri=http://localhost:3000/auth/callback",
        "app.auth.oauth2-failure-redirect-uri=http://localhost:3000/auth/callback"
})
@SuppressWarnings("NonAsciiCharacters")
class ActuatorSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2FrontendRedirectResolver oAuth2FrontendRedirectResolver;

    @MockitoBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockitoBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @MockitoBean
    private RestAccessDeniedHandler restAccessDeniedHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(restAuthenticationEntryPoint).commence(Mockito.any(), Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }).when(restAccessDeniedHandler).handle(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void actuator_prometheus는_인증_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("prometheus"));
    }

    @Test
    void actuator_health는_인증_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("health"));
    }

    @Test
    void actuator_info는_인증_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(content().string("info"));
    }

    @Test
    void 일반_api는_인증_없이_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/private-test"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    public static class TestController {

        @GetMapping(value = "/actuator/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
        public String prometheus() {
            return "prometheus";
        }

        @GetMapping("/actuator/health")
        public String health() {
            return "health";
        }

        @GetMapping("/actuator/info")
        public String info() {
            return "info";
        }

        @GetMapping("/api/v1/private-test")
        public String privateApi() {
            return "private";
        }
    }
}
