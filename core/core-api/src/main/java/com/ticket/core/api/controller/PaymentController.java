package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.PaymentControllerDocs;
import com.ticket.core.api.controller.request.ConfirmPaymentRequest;
import com.ticket.core.api.controller.request.PreparePaymentRequest;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.payment.command.ConfirmPaymentUseCase;
import com.ticket.core.domain.payment.command.PreparePaymentUseCase;
import com.ticket.core.support.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController implements PaymentControllerDocs {

    private final PreparePaymentUseCase preparePaymentUseCase;
    private final ConfirmPaymentUseCase confirmPaymentUseCase;

    @Override
    @PostMapping("/orders/{orderKey}/payments")
    public ResponseEntity<ApiResponse<PreparePaymentUseCase.Output>> preparePayment(
            @PathVariable final String orderKey,
            @Valid @RequestBody final PreparePaymentRequest request,
            final MemberPrincipal memberPrincipal
    ) {
        final PreparePaymentUseCase.Output output = preparePaymentUseCase.execute(
                new PreparePaymentUseCase.Input(
                        orderKey,
                        memberPrincipal.getMemberId(),
                        request.getMethod(),
                        request.getAmount()
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(output));
    }

    @Override
    @PostMapping("/payments/{paymentKey}/confirm")
    public ApiResponse<ConfirmPaymentUseCase.Output> confirmPayment(
            @PathVariable final String paymentKey,
            @Valid @RequestBody final ConfirmPaymentRequest request,
            final MemberPrincipal memberPrincipal
    ) {
        final ConfirmPaymentUseCase.Output output = confirmPaymentUseCase.execute(
                new ConfirmPaymentUseCase.Input(
                        paymentKey,
                        memberPrincipal.getMemberId(),
                        request.getAmount()
                )
        );
        return ApiResponse.success(output);
    }
}
