package com.ticket.member.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.application.usecase.GetCurrentMemberUseCase;
import com.ticket.member.web.docs.MemberControllerDocs;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 조회 endpoint다. 탈퇴({@code DELETE /api/v1/members})는 인증 조립이라 security.auth의 {@code
 * MemberWithdrawalController}가 갖는다 — member가 그 조립을 참조하면 module 순환이 생긴다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {
    private final GetCurrentMemberUseCase getCurrentMemberUseCase;

    @Override
    @GetMapping
    public ApiResponse<GetCurrentMemberUseCase.Output> getCurrentMember(
            final AuthenticatedMember member) {
        final GetCurrentMemberUseCase.Input input =
                new GetCurrentMemberUseCase.Input(member.memberId());
        return ApiResponse.success(getCurrentMemberUseCase.execute(input));
    }
}
