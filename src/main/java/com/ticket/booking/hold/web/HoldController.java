package com.ticket.booking.hold.web;

import com.ticket.booking.hold.web.docs.HoldControllerDocs;
import com.ticket.booking.hold.web.request.CreateHoldRequest;
import com.ticket.member.AuthenticatedMember;
import com.ticket.booking.application.usecase.CreateOrderUseCase;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Deprecated
@RestController
@RequestMapping("/api/v1/performances/{performanceId}/holds")
@RequiredArgsConstructor
public class HoldController implements HoldControllerDocs {

    private final CreateOrderUseCase createOrderUseCase;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderUseCase.Output>> createHold(
            @PathVariable final Long performanceId,
            @RequestBody final CreateHoldRequest request,
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @RequestHeader(value = "X-Admission-Token", required = false) final String admissionToken,
            final AuthenticatedMember member
    ) {
        final CreateOrderUseCase.Input input = new CreateOrderUseCase.Input(
                performanceId,
                request.getSeatIds(),
                member.memberId(),
                admissionToken
        );
        final CreateOrderUseCase.Output output = createOrderUseCase.execute(input);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + output.orderKey()))
                .header("X-Order-Key", output.orderKey())
                .body(ApiResponse.success(output));
    }
}
