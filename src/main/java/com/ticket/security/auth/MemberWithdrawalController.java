package com.ticket.security.auth;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 회원 탈퇴 endpoint다. URL({@code DELETE /api/v1/members})·인증 요구·응답은 예전과 같고, 소유자만 바뀌었다.
 *
 * <p>탈퇴는 member의 DB 처리로 끝나지 않는다 — 커밋 뒤 외부 provider 연결 해제가 이어지고, 응답 전에 SecurityContext를 비운다. 그 조립은
 * 인증의 일이라 security가 갖는다. member.endpoint는 {@code GET /api/v1/members}만 유지한다.
 *
 * <p>같은 URL을 두 module의 Controller가 나눠 갖지만 메서드가 달라 매핑이 겹치지 않는다. 이렇게 두지 않으면 member가 security의 탈퇴 조립을
 * 참조해 module 순환이 생긴다.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberWithdrawalController implements MemberWithdrawalControllerDocs {
    private final WithdrawCurrentMemberUseCase withdrawCurrentMemberUseCase;

    @Override
    @DeleteMapping
    public ApiResponse<WithdrawCurrentMemberUseCase.Output> withdrawCurrentMember(
            final AuthenticatedMember member) {
        final WithdrawCurrentMemberUseCase.Output output =
                withdrawCurrentMemberUseCase.execute(
                        new WithdrawCurrentMemberUseCase.Input(member.memberId()));
        SecurityContextHolder.clearContext();
        return ApiResponse.success(output);
    }
}
