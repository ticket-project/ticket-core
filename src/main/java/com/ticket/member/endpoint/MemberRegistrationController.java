package com.ticket.member.endpoint;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.endpoint.docs.MemberRegistrationControllerDocs;
import com.ticket.member.usecase.RegisterMemberUseCase;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 계정 생성의 HTTP 진입점이다. 기존 인증 URL을 유지한다. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class MemberRegistrationController implements MemberRegistrationControllerDocs {
    private final RegisterMemberUseCase registerMemberUseCase;

    @Override
    @PostMapping("/signup")
    public ApiResponse<RegisterMemberUseCase.Output> signUp(@RequestBody final RegisterMemberRequest request) {
        return ApiResponse.success(registerMemberUseCase.execute(request.toInput()));
    }
}
