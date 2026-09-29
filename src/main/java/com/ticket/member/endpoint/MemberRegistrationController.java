package com.ticket.member.endpoint;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.usecase.RegisterMemberUseCase;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 계정 생성의 HTTP 진입점이다. 기존 인증 URL을 유지한다. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증(Auth)", description = "회원가입, 로그인, 토큰 재발급, 로그아웃, 소셜 로그인 관련 API")
public class MemberRegistrationController {
    private final RegisterMemberUseCase registerMemberUseCase;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 이름으로 회원가입합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값이 올바르지 않음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    })
    @PostMapping("/signup")
    public ApiResponse<RegisterMemberUseCase.Output> signUp(@RequestBody @Valid final RegisterMemberRequest request) {
        return ApiResponse.success(registerMemberUseCase.execute(request.toInput()));
    }
}
