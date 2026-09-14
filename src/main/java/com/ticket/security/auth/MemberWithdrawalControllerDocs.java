package com.ticket.security.auth;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 요청 파라미터 제약은 이 문서 인터페이스에만 선언한다. 이유는 {@code MemberControllerDocs}와 같다.
 *
 * <p>Swagger tag는 예전과 같은 "회원(Member)"을 쓴다 — 소유 module이 바뀌어도 API 문서에서 보이는 자리는 그대로여야 한다.
 */
@Tag(name = "회원(Member)", description = "회원 정보 조회 API")
public interface MemberWithdrawalControllerDocs {
    @Operation(summary = "현재 회원 탈퇴", description = "로그인한 회원을 탈퇴 처리합니다.")
    @ApiResponses(
            value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "200",
                        description = "탈퇴 성공"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                        responseCode = "401",
                        description = "인증되지 않은 사용자")
            })
    ApiResponse<WithdrawCurrentMemberUseCase.Output> withdrawCurrentMember(
            @Parameter(hidden = true) AuthenticatedMember member);
}
