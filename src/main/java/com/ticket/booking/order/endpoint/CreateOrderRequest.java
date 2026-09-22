package com.ticket.booking.order.endpoint;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "주문 시작 요청")
public class CreateOrderRequest {
    // Jackson이 바인딩한 뒤 Bean Validation이 검사하므로 생성 직후에는 null이다.
    @NotNull(message = "performanceId는 null일 수 없습니다.")
    @Positive(message = "performanceId는 양수여야 합니다.")
    @Schema(description = "회차 ID", example = "10")
    private @Nullable Long performanceId;

    @NotEmpty(message = "seatIds는 비어 있을 수 없습니다.")
    @ArraySchema(schema = @Schema(description = "좌석 ID", example = "42"))
    private @Nullable List<
                    @NotNull(message = "seatIds는 null을 포함할 수 없습니다.") @Positive(message = "seatIds는 양수여야 합니다.") Long>
            seatIds;

    public CreateOrderRequest() {}
}
