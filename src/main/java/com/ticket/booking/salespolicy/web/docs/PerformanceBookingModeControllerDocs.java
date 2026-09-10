package com.ticket.booking.salespolicy.web.docs;

import com.ticket.booking.salespolicy.application.usecase.GetPerformanceBookingModeUseCase;
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
@Tag(name = "Booking Mode", description = "회차 예매 방식 조회 API")
public interface PerformanceBookingModeControllerDocs {

    @Operation(
            summary = "Get performance booking mode",
            description = """
                    회차의 예매 접수 상태와 예매 방식(DIRECT/QUEUE/UNAVAILABLE)을 인증 없이 조회한다.
                    안내용 조회이므로 실제 좌석 선택·상태·주문 API는 실행 시점에 정책을 다시 검사한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회차 판매 정책이 구성되지 않음")
    })
    ApiResponse<GetPerformanceBookingModeUseCase.Output> getPerformanceBookingMode(
            @Parameter(description = "Performance ID", example = "1", required = true) @Positive Long performanceId
    );
}
