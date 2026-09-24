package com.ticket.member.endpoint.docs;

import jakarta.validation.Valid;

import com.ticket.member.endpoint.RegisterMemberRequest;
import com.ticket.member.usecase.RegisterMemberUseCase;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 회원가입 요청의 문서와 검증 제약을 선언한다. */
@Tag(name = "인증(Auth)", description = "회원가입, 로그인, 토큰 재발급, 로그아웃, 소셜 로그인 관련 API")
public interface MemberRegistrationControllerDocs {
    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 이름으로 회원가입합니다.")
    @ApiResponses(
            value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "400",
                        description = "요청값이 올바르지 않음"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
            })
    ApiResponse<RegisterMemberUseCase.Output> signUp(@Valid RegisterMemberRequest request);
}
