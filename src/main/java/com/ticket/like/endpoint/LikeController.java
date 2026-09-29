package com.ticket.like.endpoint;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.like.domain.LikeType;
import com.ticket.like.usecase.AddLikeUseCase;
import com.ticket.like.usecase.GetLikeStatusUseCase;
import com.ticket.like.usecase.RemoveLikeUseCase;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** URL은 대상이 공연뿐이던 시절의 계약(HTTP 계약은 ADR 0008로 바꾸지 않는다)을 그대로 유지한다 — 내부적으로는 {@link LikeType#SHOW}를 고정해 넘긴다. */
@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
@Tag(name = "공연 찜", description = "공연 찜(좋아요) 관련 API")
public class LikeController {
    private final AddLikeUseCase addLikeUseCase;
    private final RemoveLikeUseCase removeLikeUseCase;
    private final GetLikeStatusUseCase getLikeStatusUseCase;

    @Operation(summary = "공연 찜 추가", description = """
            인증된 회원의 공연 찜을 추가합니다. 이미 찜한 경우에도 멱등하게 성공을 반환합니다.
            존재하지 않는 공연 id를 넘겨도 찜은 그대로 저장됩니다 — like는 공연 존재를 확인하지 않습니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "찜 추가 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/shows/{showId}")
    public ApiResponse<AddLikeUseCase.Output> likeShow(
            @Parameter(hidden = true) final AuthenticatedMember member,
            @Parameter(description = "공연 ID", example = "1") @PathVariable @Positive final Long showId) {
        final AddLikeUseCase.Input input = new AddLikeUseCase.Input(member.memberId(), LikeType.SHOW, showId);
        return ApiResponse.success(addLikeUseCase.execute(input));
    }

    @Operation(summary = "공연 찜 취소", description = "인증된 회원의 공연 찜을 취소합니다. 찜하지 않은 상태여도 멱등하게 성공을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "찜 취소 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/shows/{showId}")
    public ApiResponse<RemoveLikeUseCase.Output> unlikeShow(
            @Parameter(hidden = true) final AuthenticatedMember member,
            @Parameter(description = "공연 ID", example = "1") @PathVariable @Positive final Long showId) {
        final RemoveLikeUseCase.Input input = new RemoveLikeUseCase.Input(member.memberId(), LikeType.SHOW, showId);
        return ApiResponse.success(removeLikeUseCase.execute(input));
    }

    @Operation(summary = "공연 찜 상태 조회", description = "인증된 회원이 해당 공연을 찜했는지 여부를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/shows/{showId}")
    public ApiResponse<GetLikeStatusUseCase.Output> getLikeStatus(
            @Parameter(hidden = true) final AuthenticatedMember member,
            @Parameter(description = "공연 ID", example = "1") @PathVariable @Positive final Long showId) {
        final GetLikeStatusUseCase.Input input =
                new GetLikeStatusUseCase.Input(member.memberId(), LikeType.SHOW, showId);
        return ApiResponse.success(getLikeStatusUseCase.execute(input));
    }
}
