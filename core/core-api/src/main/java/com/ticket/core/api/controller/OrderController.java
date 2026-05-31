package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.OrderControllerDocs;
import com.ticket.core.api.controller.request.CreateOrderRequest;
import com.ticket.core.config.admission.AdmissionTokenValidator;
import com.ticket.support.passport.Passport;
import com.ticket.core.domain.order.command.cancel.CancelOrderUseCase;
import com.ticket.core.domain.order.command.create.CreateOrderUseCase;
import com.ticket.core.domain.order.query.GetOrderDetailUseCase;
import com.ticket.core.support.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final AdmissionTokenValidator admissionTokenValidator;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderUseCase.Output>> createOrder(
            @Valid @RequestBody final CreateOrderRequest request,
            @RequestHeader(value = AdmissionTokenValidator.HEADER, required = false) final String admissionToken,
            final Passport memberPrincipal
    ) {
        admissionTokenValidator.validate(request.getPerformanceId(), admissionToken);
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(
                request.getPerformanceId(),
                request.getSeatIds(),
                memberPrincipal.memberId()
        );
        final CreateOrderUseCase.Output output = createOrderUseCase.execute(input);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + output.orderKey()))
                .header("X-Order-Key", output.orderKey())
                .body(ApiResponse.success(output));
    }

    @Override
    @GetMapping("/{orderKey}")
    public ApiResponse<GetOrderDetailUseCase.Output> getOrder(
            @PathVariable final String orderKey,
            final Passport memberPrincipal
    ) {
        final GetOrderDetailUseCase.Input input = new GetOrderDetailUseCase.Input(orderKey, memberPrincipal.memberId());
        final GetOrderDetailUseCase.Output output = getOrderDetailUseCase.execute(input);
        return ApiResponse.success(output);
    }

    @Override
    @DeleteMapping("/{orderKey}")
    public ApiResponse<Void> cancelOrder(
            @PathVariable final String orderKey,
            final Passport memberPrincipal
    ) {
        cancelOrderUseCase.execute(new CancelOrderUseCase.Input(orderKey, memberPrincipal.memberId()));
        return ApiResponse.success();
    }
}
