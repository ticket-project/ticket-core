package com.ticket.core.infra.support;

import com.ticket.core.infra.show.query.BookingStatusWindowPolicy;
import com.ticket.core.infra.show.query.ShowConditionFactory;
import com.ticket.core.infra.show.query.ShowCursorPolicy;
import com.ticket.core.infra.show.query.ShowQueryHelper;
import com.ticket.core.infra.show.query.ShowSortSupport;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Querydsl 조회 어댑터 테스트의 베이스다. 조건 생성·정렬·커서 헬퍼를 빈으로 올린다.
 */
@Import({
        ShowQueryHelper.class,
        BookingStatusWindowPolicy.class,
        ShowConditionFactory.class,
        ShowSortSupport.class,
        ShowCursorPolicy.class,
        InfraReadRepositoryTestSupport.InfraJpaRepositoriesTestConfig.class
})
public abstract class InfraReadRepositoryTestSupport extends ReadRepositoryTestSupport {

    /**
     * RepositoryAdapter가 쓰는 Spring Data 인터페이스를 테스트 컨텍스트에 올린다.
     */
    @Configuration
    @EnableJpaRepositories(basePackages = "com.ticket.core.infra")
    static class InfraJpaRepositoriesTestConfig {
    }
}
