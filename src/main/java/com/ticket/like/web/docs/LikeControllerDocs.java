package com.ticket.like.web.docs;

import com.ticket.member.AuthenticatedMember;
import com.ticket.like.application.usecase.AddLikeUseCase;
import com.ticket.like.application.usecase.RemoveLikeUseCase;
import com.ticket.like.application.usecase.GetLikeStatusUseCase;
import com.ticket.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

/**
 * 요청 파라미터 제약은 이 문서 인터페이스에만 선언한다.
 *
 * <p>Jakarta Bean Validation은 상위 타입 메서드의 파라미터 제약을 구현체가 다시 선언하는 것을
 * 금지한다(ConstraintDeclarationException). Controller는 binding 애노테이션만 갖고,
 * 제약과 @Valid cascade는 여기 한곳에 둔다.
 */
@Tag(name = "공연 찜", description = "공연 찜(좋아요) 관련 API")
public interface LikeControllerDocs {

    @Operation(
            summary = "공연 찜 추가",
            description = "인증된 회원의 공연 찜을 추가합니다. 이미 찜한 경우에도 멱등하게 성공을 반환합니다. "
                    + "존재하지 않는 공연 id를 넘겨도 찜은 그대로 저장됩니다 — like는 공연 존재를 확인하지 않습니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "찜 추가 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponse<AddLikeUseCase.Output> likeShow(
            @Parameter(hidden = true) AuthenticatedMember member,
            @Parameter(description = "공연 ID", example = "1", required = true) @Positive Long showId
    );

    @Operation(
            summary = "공연 찜 취소",
            description = "인증된 회원의 공연 찜을 취소합니다. 찜하지 않은 상태여도 멱등하게 성공을 반환합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "찜 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponse<RemoveLikeUseCase.Output> unlikeShow(
            @Parameter(hidden = true) AuthenticatedMember member,
            @Parameter(description = "공연 ID", example = "1", required = true) @Positive Long showId
    );

    @Operation(
            summary = "공연 찜 상태 조회",
            description = "인증된 회원이 해당 공연을 찜했는지 여부를 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponse<GetLikeStatusUseCase.Output> getLikeStatus(
            @Parameter(hidden = true) AuthenticatedMember member,
            @Parameter(description = "공연 ID", example = "1", required = true) @Positive Long showId
    );

}
