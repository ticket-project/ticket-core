package com.ticket.core.api.controller.request;

import com.ticket.core.domain.payment.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Schema(description = "결제 준비 요청")
public class PreparePaymentRequest {

    @NotNull(message = "method는 null일 수 없습니다.")
    @Schema(description = "결제수단", example = "CARD")
    private PaymentMethod method;

    @NotNull(message = "amount는 null일 수 없습니다.")
    @Positive(message = "amount는 양수여야 합니다.")
    @Schema(description = "결제 금액", example = "120000")
    private BigDecimal amount;

    public PreparePaymentRequest() {
    }
}
