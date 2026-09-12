package com.ticket.security.infrastructure;

import com.ticket.TicketApplication;
import com.ticket.member.AccessTokenReader;
import com.ticket.member.AccessTokenReadResult;
import com.ticket.member.AuthenticatedMember;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @ContextConfiguration(classes = TicketApplication.class)}: {@code @WebMvcTest}는 명시가
 * 없으면 같은 package에서 가장 가까운 {@code @SpringBootConfiguration}을 자동 탐색하므로,
 * 진짜 애플리케이션 진입점을 명시로 고정한다.
 */
@WebMvcTest(controllers = ApiSecurityConfigTest.TestController.class)
@ContextConfiguration(classes = TicketApplication.class)
@Import({ApiSecurityConfig.class, SecurityWebMvcConfig.class, ApiSecurityConfigTest.TestController.class})
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "app.cors.allowed-origins=http://localhost:3000"
})
@SuppressWarnings("NonAsciiCharacters")
class ApiSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessTokenReader accessTokenReader;

    @MockitoBean
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @MockitoBean
    private RestAccessDeniedHandler restAccessDeniedHandler;

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

    @Test
    void 일반_api는_유효한_internal_auth_token으로_접근할_수_있다() throws Exception {
        Mockito.when(accessTokenReader.read("access-token"))
                .thenReturn(AccessTokenReadResult.authenticated(new AuthenticatedMember(7L, "MEMBER")));

        mockMvc.perform(get("/api/v1/private-test")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("7:MEMBER"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getRequest().getSession(false)).isNull());
    }

    @Test
    void 일반_api는_유효하지_않은_internal_auth_token이면_401을_반환한다() throws Exception {
        Mockito.when(accessTokenReader.read("not-a-valid-token"))
                .thenReturn(AccessTokenReadResult.invalid());

        mockMvc.perform(get("/api/v1/private-test")
                        .header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 만료된_token이면_보호_api가_401을_반환한다() throws Exception {
        Mockito.when(accessTokenReader.read("expired-token"))
                .thenReturn(AccessTokenReadResult.expired());

        mockMvc.perform(get("/api/v1/private-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Authorization_header_형식이_잘못되면_보호_api가_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/private-test")
                        .header(HttpHeaders.AUTHORIZATION, "Basic access-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인과_공연_조회_api는_인증_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isOk())
                .andExpect(content().string("login"));
        mockMvc.perform(get("/api/v1/shows/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("show"));
        mockMvc.perform(get("/api/v1/performances/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("performance"));
    }

    @Test
    void 좌석상태_조회는_일반_공연조회보다_먼저_인증을_요구한다() throws Exception {
        mockMvc.perform(get("/api/v1/performances/1/seats/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 공개_api에_유효하지_않은_token이_있어도_기존처럼_접근할_수_있다() throws Exception {
        Mockito.when(accessTokenReader.read("invalid-token"))
                .thenReturn(AccessTokenReadResult.invalid());

        mockMvc.perform(get("/api/v1/shows/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("show"));
    }

    @Test
    void 허용된_origin의_preflight는_인증_없이_처리한다() throws Exception {
        mockMvc.perform(options("/api/v1/private-test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
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
        public String privateApi(final AuthenticatedMember memberPrincipal) {
            return memberPrincipal.memberId() + ":" + memberPrincipal.role();
        }

        @PostMapping("/api/v1/auth/login")
        public String login() {
            return "login";
        }

        @GetMapping("/api/v1/shows/1")
        public String show() {
            return "show";
        }

        @GetMapping("/api/v1/performances/1")
        public String performance() {
            return "performance";
        }

        @GetMapping("/api/v1/performances/1/seats/status")
        public String seatStatus() {
            return "seat-status";
        }
    }
}
