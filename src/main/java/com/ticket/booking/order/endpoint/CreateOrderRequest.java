package com.ticket.booking.order.endpoint;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** Jackson이 바인딩한 뒤 Bean Validation이 검사하므로 컴포넌트는 바인딩 직후 null일 수 있다. */
@Schema(description = "주문 생성 요청")
public record CreateOrderRequest(
        @NotNull(message = "performanceId는 null일 수 없습니다.")
        @Positive(message = "performanceId는 양수여야 합니다.")
        @Schema(description = "회차 ID", example = "10")
        @Nullable
        Long performanceId,

        @NotEmpty(message = "seatIds는 비어 있을 수 없습니다.")
        @ArraySchema(schema = @Schema(description = "좌석 ID", example = "42"))
        @Nullable
        List<@NotNull(message = "seatIds는 null을 포함할 수 없습니다.") @Positive(message = "seatIds는 양수여야 합니다.") Long>
                seatIds) {}
