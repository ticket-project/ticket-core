package com.ticket.booking.web.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "좌석 HOLD 생성 요청")
public class CreateHoldRequest {

    @NotEmpty(message = "seatIds는 비어 있을 수 없습니다.")
    @ArraySchema(schema = @Schema(description = "좌석 ID", example = "42"))
    private List<
            @NotNull(message = "seatIds에는 null이 올 수 없습니다.")
            @Positive(message = "seatIds는 양수여야 합니다.")
            Long> seatIds;

    public CreateHoldRequest() {
    }
}
