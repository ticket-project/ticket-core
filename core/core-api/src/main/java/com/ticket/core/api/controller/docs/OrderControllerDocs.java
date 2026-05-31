package com.ticket.core.api.controller.docs;

import com.ticket.core.api.controller.request.CreateOrderRequest;
import com.ticket.support.passport.Passport;
import com.ticket.core.domain.order.command.create.CreateOrderUseCase;
import com.ticket.core.domain.order.query.GetOrderDetailUseCase;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "주문", description = "PENDING 주문 시작, 조회, 취소 API")
public interface OrderControllerDocs {

    @Operation(
            summary = "주문 시작",
            description = """
                    요청한 좌석을 선점하고 결제 진입용 PENDING 주문을 생성합니다.
                    동일 회원과 같은 공연에는 PENDING 주문을 1건만 가질 수 있습니다.
                    응답 헤더로 생성된 주문 조회 URI(Location)와 주문 키(X-Order-Key)를 반환합니다.
                    생성된 주문은 GET /api/v1/orders/{orderKey}로 조회할 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "주문 시작 성공",
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
                    description = "이미 선점된 좌석이 있거나 진행 중인 PENDING 주문이 존재",
                    content = @Content(schema = @Schema(implementation = com.ticket.core.support.response.ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<CreateOrderUseCase.Output>> createOrder(
            CreateOrderRequest request,
            @Parameter(description = "Queue Server가 발급한 admission token") String admissionToken,
            @Parameter(hidden = true) Passport memberPrincipal
    );

    @Operation(summary = "주문 조회", description = "주문/결제 화면 진입 시 호출합니다. 만료된 주문은 EXPIRED 상태로 표시됩니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 주문이 아님")
    })
    ApiResponse<GetOrderDetailUseCase.Output> getOrder(
            @Parameter(description = "주문 키", example = "ORD-3f24c6bc355148f6bf941f0b2f2a6c2b", required = true)
            String orderKey,
            @Parameter(hidden = true) Passport memberPrincipal
    );

    @Operation(summary = "주문 취소", description = "PENDING 주문과 연결된 HOLD를 취소합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PENDING 주문이 아님")
    })
    ApiResponse<Void> cancelOrder(
            @Parameter(description = "주문 키", example = "ORD-3f24c6bc355148f6bf941f0b2f2a6c2b", required = true)
            String orderKey,
            @Parameter(hidden = true) Passport memberPrincipal
    );
}
