package com.ticket.booking.seat.web.docs;

import com.ticket.member.AuthenticatedMember;
import com.ticket.booking.seat.application.usecase.GetPerformanceSeatMapUseCase;
import com.ticket.booking.seat.application.usecase.GetSeatAvailabilityUseCase;
import com.ticket.booking.seat.application.usecase.GetSeatStatusUseCase;
import com.ticket.shared.web.ApiResponse;
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
@Tag(name = "Performance", description = "회차 좌석 판매 상태 조회 API")
public interface PerformanceSeatQueryControllerDocs {

    @Operation(
            summary = "Get performance seat map",
            description = "Returns the static seat map (venue layout, seat coordinates, grade and price) for a performance."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetPerformanceSeatMapUseCase.Output> getSeatMap(
            @Parameter(description = "Performance ID", example = "1", required = true) @Positive Long performanceId
    );

    @Operation(
            summary = "Get seat availability by grade",
            description = "Returns available seat counts by grade."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetSeatAvailabilityUseCase.Output> getSeatAvailability(
            @Parameter(description = "Performance ID", example = "1", required = true) @Positive Long performanceId
    );

    @Operation(
            summary = "Get seat status",
            description = "Returns current seat status for a performance."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetSeatStatusUseCase.Output> getSeatStatus(
            @Parameter(description = "Performance ID", example = "1", required = true) @Positive Long performanceId,
            @Parameter(description = "Admission token issued by Queue Server") String admissionToken,
            @Parameter(hidden = true) AuthenticatedMember member
    );
}
