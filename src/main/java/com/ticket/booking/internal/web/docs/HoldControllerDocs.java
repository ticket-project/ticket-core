package com.ticket.booking.internal.web.docs;

import com.ticket.booking.internal.web.request.CreateHoldRequest;
import com.ticket.identity.AuthenticatedMember;
import com.ticket.booking.internal.application.order.command.CreateOrderUseCase;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * 요청 파라미터 제약은 이 문서 인터페이스에만 선언한다.
 *
 * <p>Jakarta Bean Validation은 상위 타입 메서드의 파라미터 제약을 구현체가 다시 선언하는 것을
 * 금지한다(ConstraintDeclarationException). Controller는 binding 애노테이션만 갖고,
 * 제약과 @Valid cascade는 여기 한곳에 둔다.
 */
@Deprecated
@Tag(name = "좌석 선점", description = "구형 좌석 HOLD 및 PENDING 주문 생성 API")
public interface HoldControllerDocs {

    @Deprecated
    @Operation(
            summary = "좌석 HOLD 생성",
            deprecated = true,
            description = """
                    구형 호환 API입니다. 신규 연동은 POST /api/v1/orders를 사용하세요.
                    요청한 좌석을 Redis에 HOLD 하고 DB에 PENDING 주문을 생성합니다.
                    expiresAt과 서버 기준 remainingSeconds를 함께 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "HOLD 생성 성공",
                    headers = {
                            @Header(
                                    name = "Location",
                                    description = "생성된 주문 조회 URI",
                                    schema = @Schema(type = "string", example = "/api/v1/orders/ORD-3f24c6bc355148f6bf941f0b2f2a6c2b")
                            ),
                            @Header(
                                    name = "X-Order-Key",
                                    description = "생성된 주문 키",
                                    schema = @Schema(type = "string", example = "ORD-3f24c6bc355148f6bf941f0b2f2a6c2b")
                            )
                    }
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 선점된 좌석 또는 진행 중인 PENDING 주문 존재",
                    content = @Content(schema = @Schema(implementation = com.ticket.core.support.response.ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<CreateOrderUseCase.Output>> createHold(
            @Parameter(description = "회차 ID", example = "1", required = true) @Positive Long performanceId,
            @Valid CreateHoldRequest request,
            @Parameter(description = "Queue Server가 발급한 admission token") String admissionToken,
            @Parameter(hidden = true) AuthenticatedMember member
    );
}
