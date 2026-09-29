package com.ticket.booking.order.endpoint;

import java.net.URI;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.order.usecase.CancelOrderUseCase;
import com.ticket.booking.order.usecase.GetOrderDetailUseCase;
import com.ticket.booking.order.usecase.GetOrderStatusUseCase;
import com.ticket.booking.order.usecase.StartBookingUseCase;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "주문", description = "PENDING 주문 시작, 조회, 취소 API")
public class OrderController {
    private final StartBookingUseCase startBookingUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderStatusUseCase getOrderStatusUseCase;

    @Operation(summary = "주문 시작", description = """
            요청한 좌석을 선점하고 결제 진입용 PENDING 주문을 생성합니다.
            동일 회원과 같은 공연에는 PENDING 주문을 1건만 가질 수 있습니다.
            expiresAt과 서버 기준 remainingSeconds를 함께 반환합니다.
            응답 헤더로 생성된 주문 조회 URI(Location)와 주문 키(X-Order-Key)를 반환합니다.
            생성된 주문은 GET /api/v1/orders/{orderKey}로 조회할 수 있습니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "주문 시작 성공",
                headers = {
                    @Header(name = "Location", description = "생성된 주문 조회 URI"),
                    @Header(name = "X-Order-Key", description = "생성된 주문 키")
                }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 선점된 좌석이 있거나 진행 중인 PENDING 주문이 존재")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<StartBookingUseCase.Output>> createOrder(
            @RequestBody @Valid final CreateOrderRequest request,
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @Parameter(description = "Queue Server가 발급한 admission token")
                    @RequestHeader(value = "X-Admission-Token", required = false)
                    final String admissionToken,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        // @Valid가 performanceId·seatIds의 null을 이미 400으로 거른 뒤에야 여기에 닿는다.
        final StartBookingUseCase.Input input = new StartBookingUseCase.Input(
                Objects.requireNonNull(request.getPerformanceId(), "performanceId"),
                Objects.requireNonNull(request.getSeatIds(), "seatIds"),
                member.memberId(),
                admissionToken);
        final StartBookingUseCase.Output output = startBookingUseCase.execute(input);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + output.orderKey()))
                .header("X-Order-Key", output.orderKey())
                .body(ApiResponse.success(output));
    }

    @Operation(summary = "주문 조회", description = "주문/결제 화면 진입 시 호출합니다. 만료된 주문은 EXPIRED 상태로 표시됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 주문이 아님")
    })
    @GetMapping("/{orderKey}")
    public ApiResponse<GetOrderDetailUseCase.Output> getOrder(
            @Parameter(description = "주문 키", example = "ORDER-3f24c6bc355148f6bf941f0b2f2a6c2b") @PathVariable @NotBlank
                    final String orderKey,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        final GetOrderDetailUseCase.Input input = new GetOrderDetailUseCase.Input(orderKey, member.memberId());
        final GetOrderDetailUseCase.Output output = getOrderDetailUseCase.execute(input);
        return ApiResponse.success(output);
    }

    @Operation(summary = "주문 상태 조회", description = "폴링용 경량 조회입니다. 주문 상태와 만료 정보만 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 상태 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 주문이 아님")
    })
    @GetMapping("/{orderKey}/status")
    public ApiResponse<GetOrderStatusUseCase.Output> getOrderStatus(
            @Parameter(description = "주문 키", example = "ORDER-3f24c6bc355148f6bf941f0b2f2a6c2b") @PathVariable @NotBlank
                    final String orderKey,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        final GetOrderStatusUseCase.Input input = new GetOrderStatusUseCase.Input(orderKey, member.memberId());
        final GetOrderStatusUseCase.Output output = getOrderStatusUseCase.execute(input);
        return ApiResponse.success(output);
    }

    @Operation(summary = "주문 취소", description = "PENDING 주문과 연결된 HOLD를 취소합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주문 취소 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PENDING 주문이 아님")
    })
    @DeleteMapping("/{orderKey}")
    public ApiResponse<Void> cancelOrder(
            @Parameter(description = "주문 키", example = "ORDER-3f24c6bc355148f6bf941f0b2f2a6c2b") @PathVariable @NotBlank
                    final String orderKey,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        cancelOrderUseCase.execute(new CancelOrderUseCase.Input(orderKey, member.memberId()));
        return ApiResponse.success();
    }
}
