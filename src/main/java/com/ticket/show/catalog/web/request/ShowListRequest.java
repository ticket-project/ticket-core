package com.ticket.show.catalog.web.request;

import com.ticket.show.catalog.web.support.cursor.ShowCursorCodec;
import com.ticket.show.catalog.application.ShowParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공연 목록 조회 요청이다. cursor는 HTTP wire 문자열로 받고 app에는 타입 값으로 넘긴다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShowListRequest {

    private String category;

    private String genre;

    @Schema(allowableValues = {"SEOUL", "GYEONGGI", "INCHEON", "GANGWON", "CHUNGCHEONG",
            "JEOLLA", "GYEONGSANG", "JEJU"})
    private String region;

    private String cursor;

    public ShowParam toParam(final ShowCursorCodec cursorCodec) {
        return ShowParam.of(category, genre, region, cursorCodec.decode(cursor));
    }
}
