package com.ticket.showlike.internal.web;

import com.ticket.identity.AuthenticatedMember;
import com.ticket.showlike.internal.application.command.AddShowLikeUseCase;
import com.ticket.showlike.internal.application.command.RemoveShowLikeUseCase;
import com.ticket.showlike.internal.application.query.GetShowLikeStatusUseCase;
import com.ticket.showlike.internal.web.docs.ShowLikeControllerDocs;
import com.ticket.shared.ApiResponse;
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
        final AddShowLikeUseCase.Input input = new AddShowLikeUseCase.Input(member.memberId(), showId);
        return ApiResponse.success(addShowLikeUseCase.execute(input));
    }

    @Override
    @DeleteMapping("/shows/{showId}")
    public ApiResponse<RemoveShowLikeUseCase.Output> unlikeShow(
            final AuthenticatedMember member,
            @PathVariable final Long showId
    ) {
        final RemoveShowLikeUseCase.Input input = new RemoveShowLikeUseCase.Input(member.memberId(), showId);
        return ApiResponse.success(removeShowLikeUseCase.execute(input));
    }

    @Override
    @GetMapping("/shows/{showId}")
    public ApiResponse<GetShowLikeStatusUseCase.Output> getLikeStatus(
            final AuthenticatedMember member,
            @PathVariable final Long showId
    ) {
        final GetShowLikeStatusUseCase.Input input = new GetShowLikeStatusUseCase.Input(member.memberId(), showId);
        return ApiResponse.success(getShowLikeStatusUseCase.execute(input));
    }
}
