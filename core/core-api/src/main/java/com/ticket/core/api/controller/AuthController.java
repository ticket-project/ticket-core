package com.ticket.core.api.controller;

import com.ticket.core.api.controller.request.ExchangeOAuth2TokenRequest;
import com.ticket.core.api.controller.request.LoginRequest;
import com.ticket.core.api.controller.request.RegisterMemberRequest;
import com.ticket.core.api.controller.docs.AuthControllerDocs;
import com.ticket.core.config.security.JwtProperties;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.auth.command.ExchangeOAuth2TokenUseCase;
import com.ticket.core.domain.auth.command.LoginUseCase;
import com.ticket.core.domain.auth.command.LogoutUseCase;
import com.ticket.core.domain.auth.command.RefreshAuthTokenUseCase;
import com.ticket.core.domain.auth.command.RegisterMemberUseCase;
import com.ticket.core.domain.auth.query.GetSocialLoginUrlsUseCase;
import com.ticket.core.domain.auth.token.AuthRefreshToken;
import com.ticket.core.support.response.ApiResponse;
import com.ticket.core.support.util.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

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
    private final JwtProperties jwtProperties;

    @Override
    @PostMapping("/signup")
    public ApiResponse<RegisterMemberUseCase.Output> signUp(@Valid @RequestBody final RegisterMemberRequest request) {
        return ApiResponse.success(registerMemberUseCase.execute(request.toInput()));
    }

    @Override
    @PostMapping("/login")
    public ApiResponse<LoginUseCase.Output> login(
            @Valid @RequestBody final LoginRequest request,
            final HttpServletResponse response
    ) {
        final LoginUseCase.Result result = loginUseCase.execute(request.toInput());
        addRefreshTokenCookie(response, result.refreshToken());
        return ApiResponse.success(result.output());
    }

    @Override
    @PostMapping("/refresh")
    public ApiResponse<RefreshAuthTokenUseCase.Output> refresh(
            @CookieValue(name = CookieUtils.REFRESH_TOKEN_COOKIE_NAME, required = false) final String refreshToken,
            final HttpServletResponse response
    ) {
        final RefreshAuthTokenUseCase.Input input = new RefreshAuthTokenUseCase.Input(AuthRefreshToken.from(refreshToken));
        final RefreshAuthTokenUseCase.Result result = refreshAuthTokenUseCase.execute(input);
        addRefreshTokenCookie(response, result.refreshToken());
        return ApiResponse.success(result.output());
    }

    @Override
    @PostMapping("/oauth2/token")
    public ApiResponse<ExchangeOAuth2TokenUseCase.Output> exchangeOAuth2Token(
            @Valid @RequestBody final ExchangeOAuth2TokenRequest request,
            final HttpServletResponse response
    ) {
        final ExchangeOAuth2TokenUseCase.Result result = exchangeOAuth2TokenUseCase.execute(request.toInput());
        addRefreshTokenCookie(response, result.refreshToken());
        return ApiResponse.success(result.output());
    }

    @Override
    @GetMapping("/social/urls")
    public ApiResponse<Map<String, String>> getSocialLoginUrls() {
        final String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        final GetSocialLoginUrlsUseCase.Input input = new GetSocialLoginUrlsUseCase.Input(baseUrl);
        return ApiResponse.success(getSocialLoginUrlsUseCase.execute(input).urls());
    }

    @Override
    @PostMapping("/logout")
    public ApiResponse<LogoutUseCase.Output> logout(
            @AuthenticationPrincipal final MemberPrincipal principal,
            @CookieValue(name = CookieUtils.REFRESH_TOKEN_COOKIE_NAME, required = false) final String refreshToken,
            final HttpServletResponse response
    ) {
        try {
            final LogoutUseCase.Input input = new LogoutUseCase.Input(
                    principal.getMemberId(),
                    AuthRefreshToken.from(refreshToken)
            );
            final LogoutUseCase.Output output = logoutUseCase.execute(input);
            return ApiResponse.success(output);
        } finally {
            CookieUtils.deleteRefreshTokenCookie(response);
        }
    }

    private void addRefreshTokenCookie(final HttpServletResponse response, final String refreshToken) {
        CookieUtils.addRefreshTokenCookie(
                response,
                refreshToken,
                jwtProperties.getRefreshTokenExpirationSeconds()
        );
    }
}
