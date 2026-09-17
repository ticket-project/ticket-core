package com.ticket.booking.endpoint;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "좌석 HOLD 생성 요청")
public class CreateHoldRequest {
    // Jackson이 바인딩한 뒤 Bean Validation이 검사하므로 생성 직후에는 null이다.
    @NotEmpty(message = "seatIds는 비어 있을 수 없습니다.")
    @ArraySchema(schema = @Schema(description = "좌석 ID", example = "42"))
    private @Nullable
            List<
                    @NotNull(message = "seatIds에는 null이 올 수 없습니다.")
                    @Positive(message = "seatIds는 양수여야 합니다.") Long>
            seatIds;

    public CreateHoldRequest() {}
}
