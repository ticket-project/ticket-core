package com.ticket.member.account.web.docs;

import com.ticket.member.AuthenticatedMember;
import com.ticket.member.account.application.usecase.GetCurrentMemberUseCase;
import com.ticket.member.account.application.usecase.WithdrawCurrentMemberUseCase;
import com.ticket.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 요청 파라미터 제약은 이 문서 인터페이스에만 선언한다.
 *
 * <p>Jakarta Bean Validation은 상위 타입 메서드의 파라미터 제약을 구현체가 다시 선언하는 것을
 * 금지한다(ConstraintDeclarationException). Controller는 binding 애노테이션만 갖고,
 * 제약과 @Valid cascade는 여기 한곳에 둔다.
 */
@Tag(name = "회원(Member)", description = "회원 정보 조회 API")
public interface MemberControllerDocs {

    @Operation(summary = "현재 회원 조회", description = "로그인한 회원의 정보를 조회합니다")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    ApiResponse<GetCurrentMemberUseCase.Output> getCurrentMember(
            @Parameter(hidden = true) AuthenticatedMember member
    );

    @Operation(summary = "현재 회원 탈퇴", description = "로그인한 회원을 탈퇴 처리합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    ApiResponse<WithdrawCurrentMemberUseCase.Output> withdrawCurrentMember(
            @Parameter(hidden = true) AuthenticatedMember member
    );
}
