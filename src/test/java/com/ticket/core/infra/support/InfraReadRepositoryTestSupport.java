package com.ticket.core.infra.support;

import com.ticket.catalog.internal.infrastructure.show.query.BookingStatusPredicateFactory;
import com.ticket.catalog.internal.infrastructure.show.query.QuerydslShowConditionBuilder;
import com.ticket.catalog.internal.infrastructure.show.query.QuerydslShowCursorConditionBuilder;
import com.ticket.catalog.internal.infrastructure.show.query.QuerydslShowPredicates;
import com.ticket.catalog.internal.infrastructure.show.query.QuerydslShowSortResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Querydsl 조회 어댑터 테스트의 베이스다. 조건 생성·정렬·커서 헬퍼를 빈으로 올린다.
 */
@Import({
        QuerydslShowPredicates.class,
        BookingStatusPredicateFactory.class,
        QuerydslShowConditionBuilder.class,
        QuerydslShowSortResolver.class,
        QuerydslShowCursorConditionBuilder.class,
        InfraReadRepositoryTestSupport.InfraJpaRepositoriesTestConfig.class
})
public abstract class InfraReadRepositoryTestSupport extends ReadRepositoryTestSupport {

    /**
     * RepositoryAdapter가 쓰는 Spring Data 인터페이스를 테스트 컨텍스트에 올린다.
     */
    @Configuration
    @EnableJpaRepositories(basePackages = {"com.ticket.core.infra", "com.ticket.catalog.internal.infrastructure"})
    static class InfraJpaRepositoriesTestConfig {
    }
}
