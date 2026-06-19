package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.MemberControllerDocs;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.member.query.GetCurrentMemberUseCase;
import com.ticket.core.domain.member.command.WithdrawCurrentMemberUseCase;
import com.ticket.core.domain.showlike.query.GetMyShowLikesUseCase;
import com.ticket.core.support.response.ApiResponse;
import com.ticket.core.support.response.SliceResponse;
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
    private final GetMyShowLikesUseCase getMyShowLikesUseCase;

    @Override
    @GetMapping
    public ApiResponse<GetCurrentMemberUseCase.Output> getCurrentMember(final MemberPrincipal memberPrincipal) {
        final GetCurrentMemberUseCase.Input input = new GetCurrentMemberUseCase.Input(memberPrincipal.getMemberId());
        return ApiResponse.success(getCurrentMemberUseCase.execute(input));
    }

    @Override
    @DeleteMapping
    public ApiResponse<WithdrawCurrentMemberUseCase.Output> withdrawCurrentMember(final MemberPrincipal memberPrincipal) {
        final WithdrawCurrentMemberUseCase.Input input = new WithdrawCurrentMemberUseCase.Input(memberPrincipal.getMemberId());
        final WithdrawCurrentMemberUseCase.Output output = withdrawCurrentMemberUseCase.execute(input);
        SecurityContextHolder.clearContext();
        return ApiResponse.success(output);
    }

    @Override
    @GetMapping("/me/likes")
    public ApiResponse<SliceResponse<GetMyShowLikesUseCase.ShowLikeSummary>> getMyLikes(
            final MemberPrincipal memberPrincipal,
            @RequestParam(required = false) final String cursor,
            @RequestParam(defaultValue = "20") final int size
    ) {
        final GetMyShowLikesUseCase.Input input = new GetMyShowLikesUseCase.Input(memberPrincipal.getMemberId(), cursor, size);
        final GetMyShowLikesUseCase.Output output = getMyShowLikesUseCase.execute(input);
        return ApiResponse.success(SliceResponse.from(output.shows(), output.nextCursor()));
    }
}
