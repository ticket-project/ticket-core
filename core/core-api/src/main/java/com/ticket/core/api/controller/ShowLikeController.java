package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.ShowLikeControllerDocs;
import com.ticket.core.app.auth.token.AuthenticatedMember;
import com.ticket.core.app.showlike.command.AddShowLikeUseCase;
import com.ticket.core.app.showlike.query.GetShowLikeStatusUseCase;
import com.ticket.core.app.showlike.command.RemoveShowLikeUseCase;
import com.ticket.support.error.AuthException;
import com.ticket.support.error.ErrorType;
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
            final AuthenticatedMember member,
            @PathVariable final Long showId
    ) {
        final Long memberId = requireMemberId(member);
        final AddShowLikeUseCase.Input input = new AddShowLikeUseCase.Input(memberId, showId);
        return ApiResponse.success(addShowLikeUseCase.execute(input));
    }

    @Override
    @DeleteMapping("/shows/{showId}")
    public ApiResponse<RemoveShowLikeUseCase.Output> unlikeShow(
            final AuthenticatedMember member,
            @PathVariable final Long showId
    ) {
        final Long memberId = requireMemberId(member);
        final RemoveShowLikeUseCase.Input input = new RemoveShowLikeUseCase.Input(memberId, showId);
        return ApiResponse.success(removeShowLikeUseCase.execute(input));
    }

    @Override
    @GetMapping("/shows/{showId}")
    public ApiResponse<GetShowLikeStatusUseCase.Output> getLikeStatus(
            final AuthenticatedMember member,
            @PathVariable final Long showId
    ) {
        final Long memberId = requireMemberId(member);
        final GetShowLikeStatusUseCase.Input input = new GetShowLikeStatusUseCase.Input(memberId, showId);
        return ApiResponse.success(getShowLikeStatusUseCase.execute(input));
    }

    private Long requireMemberId(final AuthenticatedMember member) {
        if (member == null || member.memberId() == null) {
            throw new AuthException(ErrorType.AUTHENTICATION_ERROR);
        }
        return member.memberId();
    }
}
