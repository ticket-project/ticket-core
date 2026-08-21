package com.ticket.core.api.controller.docs;

import com.ticket.core.api.controller.request.ConfirmPaymentRequest;
import com.ticket.core.api.controller.request.PreparePaymentRequest;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.payment.command.ConfirmPaymentUseCase;
import com.ticket.core.domain.payment.command.PreparePaymentUseCase;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "결제", description = "결제 준비와 승인 API")
public interface PaymentControllerDocs {

    @Operation(
            summary = "결제 준비",
            description = """
                    PENDING 주문에 대해 READY 상태 결제를 만들고 paymentKey를 발급한다.
                    같은 주문에 READY 결제가 이미 있으면 그 결제를 그대로 반환한다.
                    """
    )
    ResponseEntity<ApiResponse<PreparePaymentUseCase.Output>> preparePayment(
            @Parameter(description = "주문 키", example = "order-key", required = true)
            String orderKey,
            PreparePaymentRequest request,
            @Parameter(hidden = true) MemberPrincipal memberPrincipal
    );

    @Operation(
            summary = "결제 승인",
            description = """
                    결제를 승인하고 주문을 CONFIRMED로, 회차 좌석을 RESERVED로 전이시킨다.
                    이미 승인된 결제를 다시 호출하면 같은 결과를 반환한다.
                    승인이 거절되면 결제만 FAILED가 되고 주문은 PENDING으로 남는다.
                    """
    )
    ApiResponse<ConfirmPaymentUseCase.Output> confirmPayment(
            @Parameter(description = "결제 키", example = "PAY-1", required = true)
            String paymentKey,
            ConfirmPaymentRequest request,
            @Parameter(hidden = true) MemberPrincipal memberPrincipal
    );
}
