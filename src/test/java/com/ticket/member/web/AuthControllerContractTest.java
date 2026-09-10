package com.ticket.member.web;

import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.member.security.infrastructure.AuthenticatedMemberArgumentResolver;
import com.ticket.member.application.usecase.ExchangeOAuth2TokenUseCase;
import com.ticket.member.application.usecase.LoginUseCase;
import com.ticket.member.application.usecase.LogoutUseCase;
import com.ticket.member.application.usecase.RefreshAuthTokenUseCase;
import com.ticket.member.application.usecase.RegisterMemberUseCase;
import com.ticket.member.application.usecase.GetSocialLoginUrlsUseCase;
import com.ticket.error.handler.GlobalExceptionHandler;
import com.ticket.member.AuthenticatedMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class AuthControllerContractTest {

    private MockMvc mockMvc;

    private final LoginUseCase loginUseCase = Mockito.mock(LoginUseCase.class);
    private final RefreshAuthTokenUseCase refreshAuthTokenUseCase = Mockito.mock(RefreshAuthTokenUseCase.class);
    private final ExchangeOAuth2TokenUseCase exchangeOAuth2TokenUseCase = Mockito.mock(ExchangeOAuth2TokenUseCase.class);
    private final LogoutUseCase logoutUseCase = Mockito.mock(LogoutUseCase.class);

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(
                Mockito.mock(RegisterMemberUseCase.class),
                loginUseCase,
                refreshAuthTokenUseCase,
                exchangeOAuth2TokenUseCase,
                Mockito.mock(GetSocialLoginUrlsUseCase.class),
                logoutUseCase
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 로그인_API는_성공_응답과_refresh_cookie를_내린다() throws Exception {
        when(loginUseCase.execute(any(LoginUseCase.Input.class)))
                .thenReturn(new LoginUseCase.Result(
                        new LoginUseCase.Output("access-token", "Bearer", 1800L, 1L),
                        "refresh-token",
                        1209600L
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=refresh-token")))
                // 쿠키 만료는 발급 결과가 알려 준 값을 그대로 쓴다
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=1209600")))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void 리프레시_API는_쿠키가_없으면_인증_오류_계약을_지킨다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E1000"));
    }

    @Test
    void 리프레시_API는_성공_응답과_refresh_cookie를_내린다() throws Exception {
        when(refreshAuthTokenUseCase.execute(any(RefreshAuthTokenUseCase.Input.class)))
                .thenReturn(new RefreshAuthTokenUseCase.Result(
                        new RefreshAuthTokenUseCase.Output("new-access-token", "Bearer", 1800L, 1L),
                        "new-refresh-token",
                        1209600L
                ));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new MockCookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=new-refresh-token")))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void OAuth2_token_API는_성공_응답과_refresh_cookie를_내린다() throws Exception {
        when(exchangeOAuth2TokenUseCase.execute(any(ExchangeOAuth2TokenUseCase.Input.class)))
                .thenReturn(new ExchangeOAuth2TokenUseCase.Result(
                        new ExchangeOAuth2TokenUseCase.Output("oauth-access-token", "Bearer", 1800L, 7L),
                        "oauth-refresh-token",
                        1209600L
                ));

        mockMvc.perform(post("/api/v1/auth/oauth2/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "oauth-code"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("oauth-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=oauth-refresh-token")))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void 로그아웃_API는_성공_응답과_쿠키_삭제를_내린다() throws Exception {
        AuthenticatedMember principal = new AuthenticatedMember(1L, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of())
        );
        when(logoutUseCase.execute(any(LogoutUseCase.Input.class)))
                .thenReturn(new LogoutUseCase.Output());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new MockCookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")))
                .andExpect(jsonPath("$.error").isEmpty());
    }
    @Test
    void 로그아웃_API는_실패해도_refresh_cookie를_삭제한다() throws Exception {
        AuthenticatedMember principal = new AuthenticatedMember(1L, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of())
        );
        when(logoutUseCase.execute(any(LogoutUseCase.Input.class)))
                .thenThrow(new UnauthenticatedException());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new MockCookie("refresh_token", "refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}
