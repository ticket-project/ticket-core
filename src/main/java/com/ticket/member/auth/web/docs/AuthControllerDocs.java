package com.ticket.member.auth.web.docs;

import com.ticket.member.oauth.web.request.ExchangeOAuth2TokenRequest;
import com.ticket.member.auth.web.request.LoginRequest;
import com.ticket.member.web.request.RegisterMemberRequest;
import com.ticket.member.AuthenticatedMember;
import com.ticket.member.oauth.application.usecase.ExchangeOAuth2TokenUseCase;
import com.ticket.member.auth.application.usecase.LoginUseCase;
import com.ticket.member.auth.application.usecase.LogoutUseCase;
import com.ticket.member.auth.application.usecase.RefreshAuthTokenUseCase;
import com.ticket.member.application.usecase.RegisterMemberUseCase;
import com.ticket.member.oauth.application.usecase.GetSocialLoginUrlsUseCase;
import com.ticket.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;
import jakarta.validation.Valid;

/**
 * 요청 파라미터 제약은 이 문서 인터페이스에만 선언한다.
 *
 * <p>Jakarta Bean Validation은 상위 타입 메서드의 파라미터 제약을 구현체가 다시 선언하는 것을
 * 금지한다(ConstraintDeclarationException). Controller는 binding 애노테이션만 갖고,
 * 제약과 @Valid cascade는 여기 한곳에 둔다.
 */
@Tag(name = "인증(Auth)", description = "회원가입, 로그인, 토큰 재발급, 로그아웃, 소셜 로그인 관련 API")
public interface AuthControllerDocs {

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 이름으로 회원가입합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값이 올바르지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    })
    ApiResponse<RegisterMemberUseCase.Output> signUp(@Valid RegisterMemberRequest request);

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인하고 Access Token을 응답으로 반환합니다. Refresh Token은 HttpOnly 쿠키로 설정됩니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    })
    ApiResponse<LoginUseCase.Output> login(
            @Valid LoginRequest request,
            @Parameter(hidden = true) HttpServletResponse response
    );

    @Operation(
            summary = "토큰 재발급",
            description = "HttpOnly 쿠키의 Refresh Token으로 Access Token을 재발급하고, 새로운 Refresh Token으로 쿠키를 교체합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 Refresh Token")
    })
    ApiResponse<RefreshAuthTokenUseCase.Output> refresh(
            @Parameter(hidden = true) String refreshToken,
            @Parameter(hidden = true) HttpServletResponse response
    );

    @Operation(
            summary = "OAuth2 토큰 교환",
            description = "소셜 로그인 성공 후 발급된 1회용 인증 코드를 Access Token으로 교환합니다. Refresh Token은 HttpOnly 쿠키로 설정됩니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 교환 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 인증 코드")
    })
    ApiResponse<ExchangeOAuth2TokenUseCase.Output> exchangeOAuth2Token(
            @Valid ExchangeOAuth2TokenRequest request,
            @Parameter(hidden = true) HttpServletResponse response
    );

    @Operation(summary = "소셜 로그인 URL 조회", description = "Google, Kakao 소셜 로그인 진입 URL을 반환합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ApiResponse<Map<String, String>> getSocialLoginUrls();

    @Operation(
            summary = "로그아웃",
            description = "Refresh Token을 무효화하고 쿠키를 삭제합니다. Access Token은 만료 시간까지 자연스럽게 무효화됩니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ApiResponse<LogoutUseCase.Output> logout(
            @Parameter(hidden = true) AuthenticatedMember member,
            @Parameter(hidden = true) String refreshToken,
            @Parameter(hidden = true) HttpServletResponse response
    );
}
