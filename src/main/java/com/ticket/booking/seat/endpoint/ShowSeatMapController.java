package com.ticket.booking.seat.endpoint;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 기존 프론트가 사용하는 공연 단위 좌석 배치도 API다. 본문은 {@link ShowSeatMapBody}가 미리 만든 바이트를 그대로 보낸다.
 *
 * <p>gzip을 받는 요청에는 미리 압축한 바이트와 {@code Content-Encoding: gzip}을 보낸다. Core는 응답을 압축하지 않고 운영 nginx가 압축하는데, nginx는
 * {@code Content-Encoding}이 이미 있는 응답을 다시 압축하지 않는다. 응답 JSON 모양은 다른 API와 같은 {@code ApiResponse} 봉투다.
 */
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowSeatMapController {
    private final ShowSeatMapBody showSeatMapBody;

    @GetMapping("/{showId}/seats")
    public ResponseEntity<byte[]> getSeatMap(
            @PathVariable final Long showId,
            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false)
                    final @Nullable String acceptEncoding) {
        final ShowSeatMapBody.Encoded body = showSeatMapBody.read(showId);
        final ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        // ponytail: "gzip;q=0"(거절)도 gzip으로 본다. 브라우저·Gatling은 보내지 않는다.
        if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
            return response.header(HttpHeaders.CONTENT_ENCODING, "gzip").body(body.gzip());
        }
        return response.body(body.json());
    }
}
