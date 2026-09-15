package com.ticket.booking.endpoint;

import java.net.URI;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.application.usecase.CancelOrderUseCase;
import com.ticket.booking.application.usecase.GetOrderDetailUseCase;
import com.ticket.booking.application.usecase.GetOrderStatusUseCase;
import com.ticket.booking.application.usecase.StartBookingUseCase;
import com.ticket.booking.endpoint.docs.OrderControllerDocs;
import com.ticket.booking.endpoint.request.CreateOrderRequest;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {
    private final StartBookingUseCase startBookingUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final GetOrderStatusUseCase getOrderStatusUseCase;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<StartBookingUseCase.Output>> createOrder(
            @RequestBody final CreateOrderRequest request,
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @RequestHeader(value = "X-Admission-Token", required = false)
                    final String admissionToken,
            final AuthenticatedMember member) {
        // @Valid가 performanceId·seatIds의 null을 이미 400으로 거른 뒤에야 여기에 닿는다.
        final StartBookingUseCase.Input input =
                new StartBookingUseCase.Input(
                        Objects.requireNonNull(request.getPerformanceId(), "performanceId"),
                        Objects.requireNonNull(request.getSeatIds(), "seatIds"),
                        member.memberId(),
                        admissionToken);
        final StartBookingUseCase.Output output = startBookingUseCase.execute(input);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + output.orderKey()))
                .header("X-Order-Key", output.orderKey())
                .body(ApiResponse.success(output));
    }

    @Override
    @GetMapping("/{orderKey}")
    public ApiResponse<GetOrderDetailUseCase.Output> getOrder(
            @PathVariable final String orderKey, final AuthenticatedMember member) {
        final GetOrderDetailUseCase.Input input =
                new GetOrderDetailUseCase.Input(orderKey, member.memberId());
        final GetOrderDetailUseCase.Output output = getOrderDetailUseCase.execute(input);
        return ApiResponse.success(output);
    }

    @Override
    @GetMapping("/{orderKey}/status")
    public ApiResponse<GetOrderStatusUseCase.Output> getOrderStatus(
            @PathVariable final String orderKey, final AuthenticatedMember member) {
        final GetOrderStatusUseCase.Input input =
                new GetOrderStatusUseCase.Input(orderKey, member.memberId());
        final GetOrderStatusUseCase.Output output = getOrderStatusUseCase.execute(input);
        return ApiResponse.success(output);
    }

    @Override
    @DeleteMapping("/{orderKey}")
    public ApiResponse<Void> cancelOrder(
            @PathVariable final String orderKey, final AuthenticatedMember member) {
        cancelOrderUseCase.execute(new CancelOrderUseCase.Input(orderKey, member.memberId()));
        return ApiResponse.success();
    }
}
