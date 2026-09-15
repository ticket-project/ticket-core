package com.ticket.show.endpoint.request;

import org.jspecify.annotations.Nullable;

import com.ticket.show.application.ShowListParam;
import com.ticket.show.endpoint.cursor.ShowCursorCodec;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공연 목록 조회 요청이다. cursor는 HTTP wire 문자열로 받고 app에는 타입 값으로 넘긴다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShowListRequest {
    private @Nullable String category;
    private @Nullable String genre;

    @Schema(
            allowableValues = {
                "SEOUL",
                "GYEONGGI",
                "INCHEON",
                "GANGWON",
                "CHUNGCHEONG",
                "JEOLLA",
                "GYEONGSANG",
                "JEJU"
            })
    private @Nullable String region;

    private @Nullable String cursor;

    public ShowListParam toParam(final ShowCursorCodec cursorCodec) {
        return ShowListParam.of(category, genre, region, cursorCodec.decode(cursor));
    }
}
