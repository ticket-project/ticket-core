package com.ticket.member.web;

import com.ticket.member.web.docs.MemberControllerDocs;
import com.ticket.member.AuthenticatedMember;
import com.ticket.member.application.usecase.GetCurrentMemberUseCase;
import com.ticket.member.application.usecase.WithdrawCurrentMemberUseCase;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

    private final GetCurrentMemberUseCase getCurrentMemberUseCase;
    private final WithdrawCurrentMemberUseCase withdrawCurrentMemberUseCase;

    @Override
    @GetMapping
    public ApiResponse<GetCurrentMemberUseCase.Output> getCurrentMember(final AuthenticatedMember member) {
        final GetCurrentMemberUseCase.Input input = new GetCurrentMemberUseCase.Input(member.memberId());
        return ApiResponse.success(getCurrentMemberUseCase.execute(input));
    }

    @Override
    @DeleteMapping
    public ApiResponse<WithdrawCurrentMemberUseCase.Output> withdrawCurrentMember(final AuthenticatedMember member) {
        final WithdrawCurrentMemberUseCase.Input input = new WithdrawCurrentMemberUseCase.Input(member.memberId());
        final WithdrawCurrentMemberUseCase.Output output = withdrawCurrentMemberUseCase.execute(input);
        SecurityContextHolder.clearContext();
        return ApiResponse.success(output);
    }
}
