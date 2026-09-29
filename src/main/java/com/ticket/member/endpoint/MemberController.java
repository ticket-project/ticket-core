package com.ticket.member.endpoint;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.usecase.GetCurrentMemberUseCase;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 조회 endpoint다. 탈퇴({@code DELETE /api/v1/members})는 인증 조립이라 security.auth의 {@code MemberWithdrawalController}가 갖는다 —
 * member가 그 조립을 참조하면 module 순환이 생긴다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "회원(Member)", description = "회원 정보 조회·탈퇴 API")
public class MemberController {
    private final GetCurrentMemberUseCase getCurrentMemberUseCase;

    @Operation(summary = "현재 회원 조회", description = "로그인한 회원의 정보를 조회합니다")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @GetMapping
    public ApiResponse<GetCurrentMemberUseCase.Output> getCurrentMember(
            @Parameter(hidden = true) final AuthenticatedMember member) {
        final GetCurrentMemberUseCase.Input input = new GetCurrentMemberUseCase.Input(member.memberId());
        return ApiResponse.success(getCurrentMemberUseCase.execute(input));
    }
}
