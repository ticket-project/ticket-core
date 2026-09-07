package com.ticket.core.infra.support;

import com.ticket.show.infrastructure.show.query.BookingStatusPredicateFactory;
import com.ticket.show.infrastructure.show.query.QuerydslShowConditionBuilder;
import com.ticket.show.infrastructure.show.query.QuerydslShowCursorConditionBuilder;
import com.ticket.show.infrastructure.show.query.QuerydslShowPredicates;
import com.ticket.show.infrastructure.show.query.QuerydslShowSortResolver;
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
     *
     * <p>module을 추출할 때마다 이 목록에 package를 하나씩 추가해 왔다(Task 5~7). 앞으로도 module
     * 추출이 이어지므로(Task 9~10 등) 넓은 base package 하나로 통합해, 추출할 때마다 이 공유 파일을
     * 건드려 병렬 작업 간 merge 충돌이 나는 일을 없앤다. {@code com.ticket} 전체를 스캔해도 실제
     * {@code @Repository} Spring Data 인터페이스가 아닌 클래스는 걸러지므로 이전보다 넓게 잡아도
     * 안전하다.
     */
    @Configuration
    @EnableJpaRepositories(basePackages = "com.ticket")
    static class InfraJpaRepositoriesTestConfig {
    }
}
