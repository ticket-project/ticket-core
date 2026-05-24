package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.HoldControllerDocs;
import com.ticket.core.api.controller.request.CreateHoldRequest;
import com.ticket.core.config.AdmissionTokenValidator;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.order.command.create.CreateOrderUseCase;
import com.ticket.core.support.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Deprecated
@RestController
@RequestMapping("/api/v1/performances/{performanceId}/holds")
@RequiredArgsConstructor
public class HoldController implements HoldControllerDocs {

    private final CreateOrderUseCase createOrderUseCase;
    private final AdmissionTokenValidator admissionTokenValidator;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderUseCase.Output>> createHold(
            @PathVariable final Long performanceId,
            @Valid @RequestBody final CreateHoldRequest request,
            final MemberPrincipal memberPrincipal,
            final HttpServletRequest servletRequest
    ) {
        admissionTokenValidator.verify(servletRequest, memberPrincipal.getMemberId(), performanceId);
        final CreateOrderUseCase.Input input =
                new CreateOrderUseCase.Input(performanceId, request.getSeatIds(), memberPrincipal.getMemberId());
        final CreateOrderUseCase.Output output = createOrderUseCase.execute(input);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + output.orderKey()))
                .header("X-Order-Key", output.orderKey())
                .body(ApiResponse.success(output));
    }
}
