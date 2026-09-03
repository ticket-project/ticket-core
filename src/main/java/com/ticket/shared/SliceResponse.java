package com.ticket.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "무한스크롤 페이지네이션 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SliceResponse<T>(

        @Schema(description = "조회된 데이터 목록")
        List<T> items,

        @Schema(description = "다음 페이지 존재 여부 (false이면 마지막 페이지)", example = "true")
        boolean hasNext,

        @Schema(description = "요청한 페이지 크기", example = "5")
        int size,

        @Schema(description = "실제 반환된 데이터 개수", example = "5")
        int numberOfElements,

        @Schema(
                description = """
                        다음 페이지 요청을 위한 커서 값
                        - 다음 요청 시 `cursor` 파라미터에 이 값을 전달
                        - `hasNext`가 false이면 null
                        """,
                example = "eyJzb3J0IjoiUE9QVUxBUiIsImRpciI6IkRFU0MifQ"
        )
        String nextCursor
) {

    /**
     * app 조회 결과를 응답으로 옮긴다. {@code size}는 요청한 페이지 크기이고
     * {@code numberOfElements}는 실제 반환된 개수다.
     */
    public static <T> SliceResponse<T> of(
            final List<T> items,
            final boolean hasNext,
            final int size,
            final String nextCursor
    ) {
        return new SliceResponse<>(items, hasNext, size, items.size(), nextCursor);
    }
}
