package com.ticket.security.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.http.RefreshTokenCookieWriter;
import com.ticket.security.oauth.GetSocialLoginUrlsUseCase;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증(Auth)", description = "토큰 재발급, 로그아웃, 소셜 로그인 관련 API")
public class AuthController {
    private final RefreshAuthTokenUseCase refreshAuthTokenUseCase;
    private final ExchangeOAuth2TokenUseCase exchangeOAuth2TokenUseCase;
    private final GetSocialLoginUrlsUseCase getSocialLoginUrlsUseCase;
    private final LogoutUseCase logoutUseCase;

    @Operation(summary = "토큰 재발급", description = """
            HttpOnly 쿠키의 Refresh Token으로 Access Token을 재발급하고,
            새로운 Refresh Token으로 쿠키를 교체합니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "유효하지 않거나 만료된 Refresh Token")
    })
    @PostMapping("/refresh")
    public ApiResponse<RefreshAuthTokenUseCase.Output> refresh(
            @Parameter(hidden = true)
                    @CookieValue(name = RefreshTokenCookieWriter.REFRESH_TOKEN_COOKIE_NAME, required = false)
                    final String refreshToken,
            @Parameter(hidden = true) final HttpServletResponse response) {
        final RefreshAuthTokenUseCase.Input input = RefreshAuthTokenUseCase.Input.from(refreshToken);
        final RefreshAuthTokenUseCase.Result result = refreshAuthTokenUseCase.execute(input);
        addRefreshTokenCookie(response, result.refreshToken(), result.refreshTokenExpiresIn());
        return ApiResponse.success(result.output());
    }

    @Operation(summary = "OAuth2 토큰 교환", description = """
            소셜 로그인 성공 후 발급된 1회용 인증 코드를 Access Token으로 교환합니다.
            Refresh Token은 HttpOnly 쿠키로 설정됩니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 교환 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 인증 코드")
    })
    @PostMapping("/oauth2/token")
    public ApiResponse<ExchangeOAuth2TokenUseCase.Output> exchangeOAuth2Token(
            @RequestBody @Valid final ExchangeOAuth2TokenRequest request,
            @Parameter(hidden = true) final HttpServletResponse response) {
        final ExchangeOAuth2TokenUseCase.Result result = exchangeOAuth2TokenUseCase.execute(request.toInput());
        addRefreshTokenCookie(response, result.refreshToken(), result.refreshTokenExpiresIn());
        return ApiResponse.success(result.output());
    }

    @Operation(summary = "소셜 로그인 URL 조회", description = "Google, Kakao 소셜 로그인 진입 URL을 반환합니다.")
    @GetMapping("/social/urls")
    public ApiResponse<Map<String, String>> getSocialLoginUrls() {
        final String baseUrl =
                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        final GetSocialLoginUrlsUseCase.Input input = new GetSocialLoginUrlsUseCase.Input(baseUrl);
        return ApiResponse.success(getSocialLoginUrlsUseCase.execute(input).urls());
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 무효화하고 쿠키를 삭제합니다. Access Token은 만료 시간까지 자연스럽게 무효화됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping("/logout")
    public ApiResponse<LogoutUseCase.Output> logout(
            @Parameter(hidden = true) final AuthenticatedMember member,
            @Parameter(hidden = true)
                    @CookieValue(name = RefreshTokenCookieWriter.REFRESH_TOKEN_COOKIE_NAME, required = false)
                    final String refreshToken,
            @Parameter(hidden = true) final HttpServletResponse response) {
        try {
            final LogoutUseCase.Input input = LogoutUseCase.Input.of(member.memberId(), refreshToken);
            final LogoutUseCase.Output output = logoutUseCase.execute(input);
            return ApiResponse.success(output);
        } finally {
            RefreshTokenCookieWriter.deleteRefreshTokenCookie(response);
        }
    }

    /** 쿠키 만료는 토큰을 발급한 쪽이 정한 값을 그대로 쓴다. 설정을 다시 읽으면 저장소 TTL과 어긋날 수 있다. */
    private void addRefreshTokenCookie(
            final HttpServletResponse response, final String refreshToken, final long maxAgeSeconds) {
        RefreshTokenCookieWriter.addRefreshTokenCookie(response, refreshToken, maxAgeSeconds);
    }
}
