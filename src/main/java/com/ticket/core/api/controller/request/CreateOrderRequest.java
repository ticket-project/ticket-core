package com.ticket.core.api.controller.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "주문 시작 요청")
public class CreateOrderRequest {

    @NotNull(message = "performanceId는 null일 수 없습니다.")
    @Positive(message = "performanceId는 양수여야 합니다.")
    @Schema(description = "회차 ID", example = "10")
    private Long performanceId;

    @NotEmpty(message = "seatIds는 비어 있을 수 없습니다.")
    @ArraySchema(schema = @Schema(description = "좌석 ID", example = "42"))
    private List<
            @NotNull(message = "seatIds는 null을 포함할 수 없습니다.")
            @Positive(message = "seatIds는 양수여야 합니다.")
            Long> seatIds;

    public CreateOrderRequest() {
    }
}
