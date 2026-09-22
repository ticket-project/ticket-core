package com.ticket.like.endpoint;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.like.domain.LikeType;
import com.ticket.like.endpoint.docs.LikeControllerDocs;
import com.ticket.like.usecase.AddLikeUseCase;
import com.ticket.like.usecase.GetLikeStatusUseCase;
import com.ticket.like.usecase.RemoveLikeUseCase;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** URL은 대상이 공연뿐이던 시절의 계약(HTTP 계약은 ADR 0008로 바꾸지 않는다)을 그대로 유지한다 — 내부적으로는 {@link LikeType#SHOW}를 고정해 넘긴다. */
@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController implements LikeControllerDocs {
    private final AddLikeUseCase addLikeUseCase;
    private final RemoveLikeUseCase removeLikeUseCase;
    private final GetLikeStatusUseCase getLikeStatusUseCase;

    @Override
    @PostMapping("/shows/{showId}")
    public ApiResponse<AddLikeUseCase.Output> likeShow(
            final AuthenticatedMember member, @PathVariable final Long showId) {
        final AddLikeUseCase.Input input = new AddLikeUseCase.Input(member.memberId(), LikeType.SHOW, showId);
        return ApiResponse.success(addLikeUseCase.execute(input));
    }

    @Override
    @DeleteMapping("/shows/{showId}")
    public ApiResponse<RemoveLikeUseCase.Output> unlikeShow(
            final AuthenticatedMember member, @PathVariable final Long showId) {
        final RemoveLikeUseCase.Input input = new RemoveLikeUseCase.Input(member.memberId(), LikeType.SHOW, showId);
        return ApiResponse.success(removeLikeUseCase.execute(input));
    }

    @Override
    @GetMapping("/shows/{showId}")
    public ApiResponse<GetLikeStatusUseCase.Output> getLikeStatus(
            final AuthenticatedMember member, @PathVariable final Long showId) {
        final GetLikeStatusUseCase.Input input =
                new GetLikeStatusUseCase.Input(member.memberId(), LikeType.SHOW, showId);
        return ApiResponse.success(getLikeStatusUseCase.execute(input));
    }
}
