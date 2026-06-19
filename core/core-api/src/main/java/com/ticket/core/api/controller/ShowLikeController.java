package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.ShowLikeControllerDocs;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.showlike.command.AddShowLikeUseCase;
import com.ticket.core.domain.showlike.query.GetShowLikeStatusUseCase;
import com.ticket.core.domain.showlike.command.RemoveShowLikeUseCase;
import com.ticket.core.support.exception.AuthException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class ShowLikeController implements ShowLikeControllerDocs {

    private final AddShowLikeUseCase addShowLikeUseCase;
    private final RemoveShowLikeUseCase removeShowLikeUseCase;
    private final GetShowLikeStatusUseCase getShowLikeStatusUseCase;

    @Override
    @PostMapping("/shows/{showId}")
    public ApiResponse<AddShowLikeUseCase.Output> likeShow(
            final MemberPrincipal memberPrincipal,
            @PathVariable final Long showId
    ) {
        final Long memberId = requireMemberId(memberPrincipal);
        final AddShowLikeUseCase.Input input = new AddShowLikeUseCase.Input(memberId, showId);
        return ApiResponse.success(addShowLikeUseCase.execute(input));
    }

    @Override
    @DeleteMapping("/shows/{showId}")
    public ApiResponse<RemoveShowLikeUseCase.Output> unlikeShow(
            final MemberPrincipal memberPrincipal,
            @PathVariable final Long showId
    ) {
        final Long memberId = requireMemberId(memberPrincipal);
        final RemoveShowLikeUseCase.Input input = new RemoveShowLikeUseCase.Input(memberId, showId);
        return ApiResponse.success(removeShowLikeUseCase.execute(input));
    }

    @Override
    @GetMapping("/shows/{showId}")
    public ApiResponse<GetShowLikeStatusUseCase.Output> getLikeStatus(
            final MemberPrincipal memberPrincipal,
            @PathVariable final Long showId
    ) {
        final Long memberId = requireMemberId(memberPrincipal);
        final GetShowLikeStatusUseCase.Input input = new GetShowLikeStatusUseCase.Input(memberId, showId);
        return ApiResponse.success(getShowLikeStatusUseCase.execute(input));
    }

    private Long requireMemberId(final MemberPrincipal memberPrincipal) {
        if (memberPrincipal == null || memberPrincipal.getMemberId() == null) {
            throw new AuthException(ErrorType.AUTHENTICATION_ERROR);
        }
        return memberPrincipal.getMemberId();
    }
}
