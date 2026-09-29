package com.ticket.security.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 회원 탈퇴 endpoint다. URL({@code DELETE /api/v1/members})·인증 요구·응답은 예전과 같고, 소유자만 바뀌었다.
 *
 * <p>탈퇴는 member의 DB 처리로 끝나지 않는다 — 커밋 뒤 외부 provider 연결 해제가 이어지고, 응답 전에 SecurityContext를 비운다. 그 조립은 인증의 일이라 security가
 * 갖는다. member.endpoint는 {@code GET /api/v1/members}만 유지한다.
 *
 * <p>같은 URL을 두 module의 Controller가 나눠 갖지만 메서드가 달라 매핑이 겹치지 않는다. 이렇게 두지 않으면 member가 security의 탈퇴 조립을 참조해 module 순환이 생긴다.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
// Swagger tag는 예전과 같은 "회원(Member)"을 쓴다 — 소유 module이 바뀌어도 API 문서에서 보이는 자리는 그대로여야 한다.
@Tag(name = "회원(Member)", description = "회원 정보 조회·탈퇴 API")
public class MemberWithdrawalController {
    private final WithdrawCurrentMemberUseCase withdrawCurrentMemberUseCase;

    @Operation(summary = "현재 회원 탈퇴", description = "로그인한 회원을 탈퇴 처리합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @DeleteMapping
    public ApiResponse<WithdrawCurrentMemberUseCase.Output> withdrawCurrentMember(
            @Parameter(hidden = true) final AuthenticatedMember member) {
        final WithdrawCurrentMemberUseCase.Output output =
                withdrawCurrentMemberUseCase.execute(new WithdrawCurrentMemberUseCase.Input(member.memberId()));
        SecurityContextHolder.clearContext();
        return ApiResponse.success(output);
    }
}
