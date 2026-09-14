package com.ticket.security.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.ticket.member.AuthenticatedMember;
import com.ticket.security.http.RefreshTokenCookieWriter;
import com.ticket.security.oauth.ExchangeOAuth2TokenRequest;
import com.ticket.security.oauth.ExchangeOAuth2TokenUseCase;
import com.ticket.security.oauth.GetSocialLoginUrlsUseCase;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {
    private final RegisterMemberUseCase registerMemberUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshAuthTokenUseCase refreshAuthTokenUseCase;
    private final ExchangeOAuth2TokenUseCase exchangeOAuth2TokenUseCase;
    private final GetSocialLoginUrlsUseCase getSocialLoginUrlsUseCase;
    private final LogoutUseCase logoutUseCase;

    @Override
    @PostMapping("/signup")
    public ApiResponse<RegisterMemberUseCase.Output> signUp(
            @RequestBody final RegisterMemberRequest request) {
        return ApiResponse.success(registerMemberUseCase.execute(request.toInput()));
    }

    @Override
    @PostMapping("/login")
    public ApiResponse<LoginUseCase.Output> login(
            @RequestBody final LoginRequest request, final HttpServletResponse response) {
        final LoginUseCase.Result result = loginUseCase.execute(request.toInput());
        addRefreshTokenCookie(response, result.refreshToken(), result.refreshTokenExpiresIn());
        return ApiResponse.success(result.output());
    }

    @Override
    @PostMapping("/refresh")
    public ApiResponse<RefreshAuthTokenUseCase.Output> refresh(
            @CookieValue(
                            name = RefreshTokenCookieWriter.REFRESH_TOKEN_COOKIE_NAME,
                            required = false)
                    final String refreshToken,
            final HttpServletResponse response) {
        final RefreshAuthTokenUseCase.Input input = RefreshAuthTokenUseCase.Input.of(refreshToken);
        final RefreshAuthTokenUseCase.Result result = refreshAuthTokenUseCase.execute(input);
        addRefreshTokenCookie(response, result.refreshToken(), result.refreshTokenExpiresIn());
        return ApiResponse.success(result.output());
    }

    @Override
    @PostMapping("/oauth2/token")
    public ApiResponse<ExchangeOAuth2TokenUseCase.Output> exchangeOAuth2Token(
            @RequestBody final ExchangeOAuth2TokenRequest request,
            final HttpServletResponse response) {
        final ExchangeOAuth2TokenUseCase.Result result =
                exchangeOAuth2TokenUseCase.execute(request.toInput());
        addRefreshTokenCookie(response, result.refreshToken(), result.refreshTokenExpiresIn());
        return ApiResponse.success(result.output());
    }

    @Override
    @GetMapping("/social/urls")
    public ApiResponse<Map<String, String>> getSocialLoginUrls() {
        final String baseUrl =
                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        final GetSocialLoginUrlsUseCase.Input input = new GetSocialLoginUrlsUseCase.Input(baseUrl);
        return ApiResponse.success(getSocialLoginUrlsUseCase.execute(input).urls());
    }

    @Override
    @PostMapping("/logout")
    public ApiResponse<LogoutUseCase.Output> logout(
            final AuthenticatedMember member,
            @CookieValue(
                            name = RefreshTokenCookieWriter.REFRESH_TOKEN_COOKIE_NAME,
                            required = false)
                    final String refreshToken,
            final HttpServletResponse response) {
        try {
            final LogoutUseCase.Input input =
                    LogoutUseCase.Input.of(member.memberId(), refreshToken);
            final LogoutUseCase.Output output = logoutUseCase.execute(input);
            return ApiResponse.success(output);
        } finally {
            RefreshTokenCookieWriter.deleteRefreshTokenCookie(response);
        }
    }

    /** 쿠키 만료는 토큰을 발급한 쪽이 정한 값을 그대로 쓴다. 설정을 다시 읽으면 저장소 TTL과 어긋날 수 있다. */
    private void addRefreshTokenCookie(
            final HttpServletResponse response,
            final String refreshToken,
            final long maxAgeSeconds) {
        RefreshTokenCookieWriter.addRefreshTokenCookie(response, refreshToken, maxAgeSeconds);
    }
}
