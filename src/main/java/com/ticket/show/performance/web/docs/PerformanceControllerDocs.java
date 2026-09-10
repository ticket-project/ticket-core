package com.ticket.show.performance.web.docs;

import com.ticket.show.performance.application.usecase.GetPerformanceScheduleListUseCase;
import com.ticket.show.performance.application.usecase.GetPerformanceSummaryUseCase;
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
@Tag(name = "Performance", description = "Performance APIs")
public interface PerformanceControllerDocs {

    @Operation(summary = "Get performance summary")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetPerformanceSummaryUseCase.Output> getPerformanceSummary(
            @Parameter(description = "Performance ID", example = "1", required = true) @Positive Long performanceId
    );

    @Operation(summary = "Get performance schedules")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetPerformanceScheduleListUseCase.Output> getPerformanceSchedules(
            @Parameter(description = "Performance ID", example = "1", required = true) @Positive Long performanceId
    );
}
