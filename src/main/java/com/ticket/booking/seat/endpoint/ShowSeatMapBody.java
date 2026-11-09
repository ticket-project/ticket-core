package com.ticket.booking.seat.endpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ticket.booking.seat.usecase.GetShowSeatMapUseCase;
import com.ticket.shared.api.CacheNames;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공연 좌석 배치도 응답 본문을 JSON 바이트와 gzip 바이트로 만들어 공연별로 10분 재사용한다({@link CacheNames#SHOW_SEAT_MAP}).
 *
 * <p><b>왜 객체가 아니라 응답 바이트를 담는가.</b> 예매 화면에 들어오는 사용자마다 한 번 부르는 응답(15,000석이면 JSON 2.2MB, gzip 134KB)이다. 조합 결과만 담으면 요청마다
 * JSON 변환(약 13ms)과 응답 gzip(약 15ms, 당시 Tomcat 압축)이 남는다(gatling-test capacity-log 2026-11-05, 운영 호스트에서 Core 직접 호출). 결과는
 * 모든 사용자에게 같고 좌석 상태를 담지 않아 판정과 무관하다. 등급 이름·가격·좌표가 바뀌면 화면에 최대 10분 옛 값이 보인다.
 *
 * <p><b>캐시를 use case 밖에 둔다.</b> {@code sync = true}라 적재하는 동안 같은 공연을 찾는 요청은 기다린다. 읽기 트랜잭션은 use case 안에서만 열리므로 기다리는 요청은
 * DB connection을 쥐지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ShowSeatMapBody {
    private final GetShowSeatMapUseCase getShowSeatMapUseCase;
    private final JsonMapper jsonMapper;

    /** 같은 본문의 압축 전·후 바이트다. 호출한 쪽은 고치지 않는다. */
    @SuppressWarnings("ArrayRecordComponent")
    public record Encoded(byte[] json, byte[] gzip) {}

    @Cacheable(cacheNames = CacheNames.SHOW_SEAT_MAP, key = "#showId", sync = true)
    public Encoded read(final long showId) {
        final byte[] json = jsonMapper.writeValueAsBytes(
                ApiResponse.success(getShowSeatMapUseCase.execute(new GetShowSeatMapUseCase.Input(showId))));
        return new Encoded(json, gzip(json));
    }

    private static byte[] gzip(final byte[] json) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(json.length / 8);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(json);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
