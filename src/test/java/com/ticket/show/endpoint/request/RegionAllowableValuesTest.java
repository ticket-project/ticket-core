package com.ticket.show.endpoint.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ticket.venue.domain.Region;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지역 코드 값 집합의 owner는 venue({@link Region})다. show의 공개 API는 그 코드를 문자열로 받고, Swagger 문서에만 허용 값을 적어 둔다 — 그 목록이 손으로 적힌 복사본이라
 * venue가 지역을 늘리거나 줄여도 아무도 모른 채 어긋난다.
 *
 * <p>여기서 고정하는 것은 "문서가 값 집합과 같다"는 사실 하나뿐이다. venue domain enum을 다시 밖으로 노출하거나 공용 enum을 새로 만들지 않고 drift만 막는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class RegionAllowableValuesTest {
    @Test
    void 요청_DTO의_지역_허용값은_venue의_Region과_같다() throws Exception {
        final String[] expected = Arrays.stream(Region.values()).map(Enum::name).toArray(String[]::new);

        for (final Class<?> requestType :
                List.of(ShowListRequest.class, ShowSearchRequest.class, SaleOpeningSoonRequest.class)) {
            final Field region = requestType.getDeclaredField("region");
            final Schema schema = region.getAnnotation(Schema.class);
            assertThat(schema)
                    .as("%s.region에 지역 허용값 문서가 있어야 한다", requestType.getSimpleName())
                    .isNotNull();
            assertThat(schema.allowableValues())
                    .as("%s.region", requestType.getSimpleName())
                    .containsExactlyInAnyOrder(expected);
        }
    }
}
