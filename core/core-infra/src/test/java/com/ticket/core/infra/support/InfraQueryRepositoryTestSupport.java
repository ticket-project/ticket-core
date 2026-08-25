package com.ticket.core.infra.support;

import com.ticket.core.domain.support.QueryRepositoryTestSupport;
import com.ticket.core.infra.show.query.BookingStatusWindowPolicy;
import com.ticket.core.infra.show.query.ShowConditionFactory;
import com.ticket.core.infra.show.query.ShowCursorPolicy;
import com.ticket.core.infra.show.query.ShowQueryHelper;
import com.ticket.core.infra.show.query.ShowSortSupport;
import com.ticket.core.app.support.cursor.CursorCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/**
 * Querydsl 조회 어댑터 테스트의 베이스다. 조건 생성·정렬·커서 헬퍼는 core-infra 소속이라
 * core-domain testFixtures가 등록할 수 없어 여기에서 빈으로 올린다.
 */
@Import({
        ShowQueryHelper.class,
        BookingStatusWindowPolicy.class,
        ShowConditionFactory.class,
        ShowSortSupport.class,
        ShowCursorPolicy.class,
        InfraQueryRepositoryTestSupport.CursorCodecTestConfig.class
})
public abstract class InfraQueryRepositoryTestSupport extends QueryRepositoryTestSupport {

    static class CursorCodecTestConfig {

        @Bean
        CursorCodec cursorCodec() {
            return new CursorCodec(JsonMapper.builder().build());
        }
    }
}
